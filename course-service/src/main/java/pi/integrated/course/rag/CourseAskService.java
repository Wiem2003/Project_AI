package pi.integrated.course.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pi.integrated.course.ai.GeminiClient;
import pi.integrated.course.ai.GeminiException;
import pi.integrated.course.config.CourseAiProperties;
import pi.integrated.course.dto.CourseAskResponse;
import pi.integrated.course.rag.CourseRetrievalService.RetrievalResult;
import pi.integrated.course.rag.CourseRetrievalService.RetrievedChunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** "Ask the course": retrieval in one course, then a grounded answer with server-built citations. */
@Service
@RequiredArgsConstructor
public class CourseAskService {

    private static final Logger log = LoggerFactory.getLogger(CourseAskService.class);

    public static final String NOT_FOUND_ANSWER = "Je ne trouve pas cette information dans le cours.";
    public static final String INDEXING_ANSWER = "Le contenu de ce cours est en cours d'indexation (transcription des vidéos). "
            + "Réessayez dans une ou deux minutes.";
    static final int MAX_QUESTION_LENGTH = 1000;

    private static final Map<String, Object> ANSWER_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "answer", Map.of("type", "STRING"),
                    "insufficient", Map.of("type", "BOOLEAN")),
            "required", List.of("answer", "insufficient"),
            "propertyOrdering", List.of("answer", "insufficient"));

    private final CourseRetrievalService retrievalService;
    private final GeminiClient geminiClient;
    private final CourseAiProperties aiProperties;
    private final ObjectMapper objectMapper;
    /** May be null outside the Spring context (evaluation test). */
    private final CourseRagIngestionService ingestionService;

    public CourseAskResponse ask(Long courseId, String question, Long currentLessonId) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is required");
        }
        if (q.length() > MAX_QUESTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Question too long (max " + MAX_QUESTION_LENGTH + " characters)");
        }

        try {
            RetrievalResult retrieval = retrievalService.retrieve(courseId, q, currentLessonId);
            if (!retrieval.hasPassages()) {
                log.info("Course {}: no passage above threshold (best similarity {}), LLM not called",
                        courseId, String.format("%.3f", retrieval.bestSimilarity()));
                if (ingestionService != null && ingestionService.isIndexing(courseId)) {
                    // the answer may be in a video still being transcribed
                    return CourseAskResponse.builder().answer(INDEXING_ANSWER).answered(false)
                            .citations(List.of()).build();
                }
                return notFound();
            }
            return generate(courseId, q, retrieval.passages());
        } catch (GeminiException e) {
            log.warn("Course {}: ask failed — {}", courseId, e.getMessage());
            HttpStatus status = e.getStatusCode() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
            throw new ResponseStatusException(status, "The course assistant is temporarily unavailable. Try again later.");
        }
    }

    private CourseAskResponse generate(Long courseId, String question, List<RetrievedChunk> passages) {
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", 2048);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", ANSWER_SCHEMA);
        generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));

        Map<String, Object> body = new HashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(GeminiClient.textPart(CourseAskPrompts.systemPrompt()))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(GeminiClient.textPart(CourseAskPrompts.userPrompt(question, passages))))));
        body.put("generationConfig", generationConfig);

        String raw = geminiClient.generateContent(aiProperties.getChatModel(), body);
        final JsonNode json;
        try {
            json = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new GeminiException("Unparseable answer from Gemini", e);
        }
        String answer = json.path("answer").asText("").trim();
        boolean insufficient = json.path("insufficient").asBoolean(false);

        CitationResolver.Resolved resolved = CitationResolver.resolve(answer, passages, courseId);
        if (insufficient) {
            // Keep the model's explanation (in the student's language) and any partial, cited answer.
            return CourseAskResponse.builder()
                    .answer(resolved.answer().isBlank() ? NOT_FOUND_ANSWER : resolved.answer())
                    .answered(!resolved.citations().isEmpty())
                    .citations(resolved.citations())
                    .build();
        }
        if (resolved.citations().isEmpty()) {
            // An answer that cites nothing is not grounded in the course: do not show it.
            log.warn("Course {}: answer without any valid citation discarded", courseId);
            return notFound();
        }
        return CourseAskResponse.builder()
                .answer(resolved.answer())
                .answered(true)
                .citations(resolved.citations())
                .build();
    }

    private static CourseAskResponse notFound() {
        return CourseAskResponse.builder()
                .answer(NOT_FOUND_ANSWER)
                .answered(false)
                .citations(List.of())
                .build();
    }
}
