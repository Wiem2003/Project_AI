package pi.integrated.preevaluation.ai;

import pi.integrated.preevaluation.config.PreevaluationAiProperties;

public final class GroqApiSupport {

    private GroqApiSupport() {
    }

    public static String resolveBaseUrl(PreevaluationAiProperties aiProperties) {
        String custom = aiProperties.getBaseUrl();
        if (custom != null && !custom.isBlank()) {
            String b = custom.trim();
            return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        }
        return switch (aiProperties.getProvider()) {
            case GROQ -> "https://api.groq.com/openai";
            case OPENAI -> "https://api.openai.com";
        };
    }

    public static String resolveChatModel(PreevaluationAiProperties aiProperties) {
        String custom = aiProperties.getModel();
        if (custom != null && !custom.isBlank()) {
            return custom.trim();
        }
        return switch (aiProperties.getProvider()) {
            case GROQ -> "openai/gpt-oss-20b";
            case OPENAI -> "gpt-4o-mini";
        };
    }

    public static String resolveEmbeddingModel(PreevaluationAiProperties aiProperties) {
        String custom = aiProperties.getEmbeddingModel();
        if (custom != null && !custom.isBlank()) {
            return custom.trim();
        }
        return "nomic-embed-text-v1_5";
    }
}
