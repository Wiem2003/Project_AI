package pi.integrated.preevaluation.rag;

import lombok.Builder;

@Builder
public record RagQueryContext(
        String questionText,
        String studentAnswer,
        String correctAnswer,
        String category,
        String studentLevel,
        String userMessage,
        FollowUpIntent followUpIntent
) {
    public String combinedQueryText() {
        StringBuilder sb = new StringBuilder();
        append(sb, questionText);
        append(sb, studentAnswer);
        append(sb, correctAnswer);
        append(sb, category);
        append(sb, studentLevel);
        if (followUpIntent != null) {
            append(sb, followUpIntent.name());
        }
        append(sb, userMessage);
        return sb.toString().trim();
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part.trim());
        }
    }
}
