package pi.integrated.course.ai;

public class GeminiException extends RuntimeException {

    private final int statusCode;

    public GeminiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GeminiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    /** HTTP status returned by Gemini, or 0 for network / parsing errors. */
    public int getStatusCode() {
        return statusCode;
    }
}
