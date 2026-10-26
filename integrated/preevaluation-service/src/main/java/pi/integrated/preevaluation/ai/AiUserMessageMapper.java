package pi.integrated.preevaluation.ai;

public final class AiUserMessageMapper {

    private AiUserMessageMapper() {
    }

    public static String toUserFriendlyError(String providerMessage, int httpStatus) {
        if (providerMessage != null) {
            String lower = providerMessage.toLowerCase();
            if (lower.contains("does not exist") || lower.contains("model") && lower.contains("access")) {
                return "The AI assistant is not available with the current model configuration. "
                        + "Ask your administrator to set GROQ_MODEL (for example openai/gpt-oss-20b).";
            }
            if (lower.contains("invalid api key") || lower.contains("incorrect api key")) {
                return "The AI assistant is not configured correctly. The server API key is missing or invalid.";
            }
            if (lower.contains("rate limit") || lower.contains("too many requests")) {
                return "The AI assistant is busy. Please wait a moment and try again.";
            }
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return "The AI assistant could not authenticate with the provider. Check GROQ_API_KEY on the server.";
        }
        if (httpStatus == 502 || httpStatus == 503 || httpStatus == 504) {
            return "The AI service is temporarily unavailable. Please try again later.";
        }
        return "The AI assistant could not complete your request. Please try again later.";
    }
}
