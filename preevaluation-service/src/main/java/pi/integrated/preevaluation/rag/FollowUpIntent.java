package pi.integrated.preevaluation.rag;

public enum FollowUpIntent {
    GENERAL,
    EXPLAIN_MORE,
    ANOTHER_EXAMPLE,
    SIMPLIFY,
    EXERCISE;

    public static FollowUpIntent detect(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return GENERAL;
        }
        String m = userMessage.toLowerCase().trim();
        if (containsAny(m, "another example", "one more example", "give me an example", "more examples")) {
            return ANOTHER_EXAMPLE;
        }
        if (containsAny(m, "simplify", "easier", "simple words", "explain simply")) {
            return SIMPLIFY;
        }
        if (containsAny(m, "exercise", "practice", "quiz me", "test me")) {
            return EXERCISE;
        }
        if (containsAny(m, "explain more", "more detail", "tell me more", "why")) {
            return EXPLAIN_MORE;
        }
        return GENERAL;
    }

    private static boolean containsAny(String message, String... phrases) {
        for (String p : phrases) {
            if (message.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
