package pi.integrated.preevaluation.rag;

import pi.integrated.preevaluation.rag.model.RetrievedPassage;

import java.util.List;
import java.util.stream.Collectors;

public final class RagPromptTemplates {

    private RagPromptTemplates() {
    }

    public static String systemPromptForMistake() {
        return """
                You are a friendly English teacher for LearnifyEnglish pre-evaluation review.
                Use ONLY the retrieved pedagogical excerpts below for factual grammar/vocabulary rules.
                If excerpts are insufficient, say clearly what is missing — do not invent a rule as certain fact.
                Structure your answer with these sections (short headings or bullets):
                1) Why the student's answer is incorrect
                2) The correct answer
                3) The English rule (from excerpts when possible)
                4) Simple examples suited to the student's CEFR level
                5) One short memory tip
                Write in clear English adapted to the student's level. Do not mention that you are an AI.""";
    }

    public static String systemPromptForFollowUp(FollowUpIntent intent) {
        String extra = switch (intent) {
            case ANOTHER_EXAMPLE -> "Provide one new example at the student's level, consistent with the excerpts.";
            case SIMPLIFY -> "Use simpler words and shorter sentences while staying accurate to the excerpts.";
            case EXERCISE -> "Give one short practice exercise with the answer hidden at the end (one line).";
            case EXPLAIN_MORE -> "Add more detail from the excerpts; do not repeat the whole first explanation verbatim.";
            case GENERAL -> "Answer the follow-up briefly and stay on the same test question.";
        };
        return """
                You continue helping the same student about the same pre-evaluation mistake.
                Ground factual claims in the retrieved excerpts when available.
                If excerpts do not cover the follow-up, state the limitation clearly.
                Do not change the correct answer. Do not discuss unrelated topics. Do not mention that you are an AI.
                """
                + extra;
    }

    public static String formatRetrievedContext(List<RetrievedPassage> passages) {
        if (passages == null || passages.isEmpty()) {
            return "(No matching pedagogical document was retrieved. Explain carefully and state that course materials did not cover this point.)";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (RetrievedPassage p : passages) {
            sb.append("[Doc ").append(i++).append(" — ").append(p.getDocumentId()).append(" | ")
                    .append(p.getRuleTitle()).append("]\n");
            sb.append(p.getPassageText()).append("\n\n");
        }
        return sb.toString().trim();
    }

    public static String userPromptForMistake(String question, String studentAnswer, String correctAnswer,
                                              String category, String level, List<RetrievedPassage> passages) {
        return """
                Student CEFR level: %s
                Category: %s

                Test question:
                %s

                Student answer: %s
                Correct answer: %s

                Retrieved pedagogical excerpts:
                %s

                Explain the mistake for the student following the required structure.""".formatted(
                level, category, question, studentAnswer, correctAnswer, formatRetrievedContext(passages));
    }

    public static String followUpSystemBlock(String question, String studentAnswer, String correctAnswer,
                                             String category, String level, String initialExplanation,
                                             List<RetrievedPassage> passages, FollowUpIntent intent) {
        return systemPromptForFollowUp(intent) + """

                ---
                Question (%s, level %s): %s
                Student's wrong answer: %s
                Correct answer: %s
                ---
                Initial explanation already shown:
                %s
                ---
                Retrieved pedagogical excerpts:
                %s
                ---""".formatted(
                category, level, question, studentAnswer, correctAnswer,
                initialExplanation, formatRetrievedContext(passages));
    }

    public static List<String> sourceLabels(List<RetrievedPassage> passages) {
        if (passages == null || passages.isEmpty()) {
            return List.of();
        }
        return passages.stream()
                .map(p -> p.getRuleTitle() + " (" + p.getDocumentId() + ")")
                .distinct()
                .collect(Collectors.toList());
    }
}
