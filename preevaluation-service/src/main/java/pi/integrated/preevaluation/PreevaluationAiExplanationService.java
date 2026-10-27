package pi.integrated.preevaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pi.integrated.preevaluation.ai.GroqChatClient;
import pi.integrated.preevaluation.config.PreevaluationAiProperties;
import pi.integrated.preevaluation.dto.*;
import pi.integrated.preevaluation.entity.Option;
import pi.integrated.preevaluation.entity.PreevaluationAnswer;
import pi.integrated.preevaluation.entity.Question;
import pi.integrated.preevaluation.rag.*;
import pi.integrated.preevaluation.rag.model.RetrievedPassage;
import pi.integrated.preevaluation.repository.PreevaluationAnswerRepository;
import pi.integrated.preevaluation.repository.PreevaluationResultRepository;
import pi.integrated.preevaluation.repository.QuestionRepository;
import pi.integrated.preevaluation.security.JwtRoleNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class PreevaluationAiExplanationService {

    private static final int MAX_INITIAL_EXPLANATION_CHARS = 12_000;
    private static final int MAX_CHAT_MESSAGE_CHARS = 4_000;
    private static final int MAX_USER_FOLLOWUP_CHARS = 2_000;
    private static final int MAX_PRIOR_MESSAGES = 10;

    private final PreevaluationAiProperties aiProperties;
    private final GroqChatClient groqChatClient;
    private final RagRetrievalService ragRetrievalService;
    private final PreevaluationResultRepository resultRepository;
    private final PreevaluationAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public ExplainMistakeResponse explainMistake(String userEmail, String role, ExplainMistakeRequest request) {
        requireStudent(role);
        if (resultRepository.findByUserEmail(userEmail).isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Pre-evaluation not completed yet");
        }

        MistakeContext ctx = loadMistakeContext(userEmail, request.getQuestionId());
        String apiKey = requireApiKey();

        RagQueryContext query = RagQueryContext.builder()
                .questionText(ctx.question().getText())
                .studentAnswer(ctx.studentText())
                .correctAnswer(ctx.correctText())
                .category(ctx.question().getCategory().name())
                .studentLevel(ctx.question().getLevel().name())
                .build();

        List<RetrievedPassage> passages = ragRetrievalService.retrieve(apiKey, query);
        String userPrompt = RagPromptTemplates.userPromptForMistake(
                ctx.question().getText(),
                ctx.studentText(),
                ctx.correctText(),
                ctx.question().getCategory().name(),
                ctx.question().getLevel().name(),
                passages);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", RagPromptTemplates.systemPromptForMistake()),
                Map.of("role", "user", "content", userPrompt)
        );

        String explanation = groqChatClient.chatCompletion(apiKey, messages, 0.35, 900);
        return ExplainMistakeResponse.builder()
                .explanation(explanation)
                .sources(RagPromptTemplates.sourceLabels(passages))
                .build();
    }

    @Transactional(readOnly = true)
    public FollowUpChatResponse followUpChat(String userEmail, String role, FollowUpChatRequest request) {
        requireStudent(role);
        if (resultRepository.findByUserEmail(userEmail).isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Pre-evaluation not completed yet");
        }

        MistakeContext ctx = loadMistakeContext(userEmail, request.getQuestionId());

        List<ChatMessageDto> prior = new ArrayList<>(
                request.getPriorMessages() == null ? List.of() : request.getPriorMessages());
        if (prior.size() > MAX_PRIOR_MESSAGES) {
            prior = new ArrayList<>(prior.subList(prior.size() - MAX_PRIOR_MESSAGES, prior.size()));
        }
        validatePriorMessages(prior);

        String apiKey = requireApiKey();
        String initial = truncate(request.getInitialExplanation(), MAX_INITIAL_EXPLANATION_CHARS);
        String userMsg = truncate(request.getUserMessage().trim(), MAX_USER_FOLLOWUP_CHARS);
        FollowUpIntent intent = FollowUpIntent.detect(userMsg);

        RagQueryContext query = RagQueryContext.builder()
                .questionText(ctx.question().getText())
                .studentAnswer(ctx.studentText())
                .correctAnswer(ctx.correctText())
                .category(ctx.question().getCategory().name())
                .studentLevel(ctx.question().getLevel().name())
                .userMessage(userMsg)
                .followUpIntent(intent)
                .build();

        List<RetrievedPassage> passages = ragRetrievalService.retrieve(apiKey, query);

        String systemContent = RagPromptTemplates.followUpSystemBlock(
                ctx.question().getText(),
                ctx.studentText(),
                ctx.correctText(),
                ctx.question().getCategory().name(),
                ctx.question().getLevel().name(),
                initial,
                passages,
                intent);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemContent));
        messages.add(Map.of("role", "assistant", "content", initial));

        for (ChatMessageDto m : prior) {
            String roleN = normalizeChatRole(m.getRole());
            messages.add(Map.of("role", roleN, "content", truncate(m.getContent(), MAX_CHAT_MESSAGE_CHARS)));
        }
        messages.add(Map.of("role", "user", "content", userMsg));

        String reply = groqChatClient.chatCompletion(apiKey, messages, 0.45, 900);
        return FollowUpChatResponse.builder()
                .reply(reply)
                .sources(RagPromptTemplates.sourceLabels(passages))
                .build();
    }

    private MistakeContext loadMistakeContext(String userEmail, Long questionId) {
        List<PreevaluationAnswer> wrong = answerRepository.findByUserEmailAndIsCorrectFalse(userEmail);
        Optional<PreevaluationAnswer> match = wrong.stream()
                .filter(a -> a.getQuestion().getId().equals(questionId))
                .findFirst();
        if (match.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "This question is not in your incorrect answers");
        }

        Question q = questionRepository.findAllWithOptionsByIdIn(List.of(questionId)).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Question not found"));

        PreevaluationAnswer row = match.get();
        String studentText = row.getSelectedOption().getText();
        String correctText = q.getOptions().stream()
                .filter(Option::isCorrect)
                .map(Option::getText)
                .findFirst()
                .orElse("");

        return new MistakeContext(q, studentText, correctText);
    }

    private String requireApiKey() {
        String apiKey = aiProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "AI explanations are not configured. Set GROQ_API_KEY (https://console.groq.com/keys) "
                            + "or OpenAI with preevaluation.ai.provider=openai and OPENAI_API_KEY.");
        }
        return apiKey.trim();
    }

    private static void validatePriorMessages(List<ChatMessageDto> prior) {
        if (prior == null || prior.isEmpty()) {
            return;
        }
        String expected = "user";
        for (ChatMessageDto m : prior) {
            String r = m.getRole() == null ? "" : m.getRole().trim().toLowerCase();
            if (!"user".equals(r) && !"assistant".equals(r)) {
                throw new ResponseStatusException(BAD_REQUEST, "Invalid message role");
            }
            if (!expected.equals(r)) {
                throw new ResponseStatusException(BAD_REQUEST, "Invalid chat history order (expected alternating user/assistant).");
            }
            expected = "user".equals(r) ? "assistant" : "user";
        }
        if (!"user".equals(expected)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid chat history (expected complete assistant replies before a new question).");
        }
    }

    private static String normalizeChatRole(String role) {
        if (role == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid message role");
        }
        String r = role.trim().toLowerCase();
        if (!"user".equals(r) && !"assistant".equals(r)) {
            throw new ResponseStatusException(BAD_REQUEST, "Message role must be user or assistant");
        }
        return r;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private void requireStudent(String role) {
        String r = JwtRoleNormalizer.normalize(role);
        if (!"STUDENT".equalsIgnoreCase(r)) {
            throw new ResponseStatusException(FORBIDDEN, "Pre-evaluation is for students only");
        }
    }

    private record MistakeContext(Question question, String studentText, String correctText) {
    }
}
