package pi.integrated.course.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "course.ai")
public class CourseAiProperties {

    /** Gemini API key — env GEMINI_API_KEY, never committed. */
    private String apiKey = "";

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    private String chatModel = "gemini-2.5-flash";

    /** Model used to transcribe lesson videos (YouTube URLs are passed directly to Gemini). */
    private String transcriptionModel = "gemini-2.5-flash";

    private String embeddingModel = "gemini-embedding-001";

    /** Output dimensionality requested from the embedding model. */
    private int embeddingDimension = 768;

    /** Retries on HTTP 429 / 5xx with exponential backoff. */
    private int maxRetries = 3;

    /** Separate key for the lesson quiz generator (env GEMINI_QUIZ_API_KEY), so it has its own quota. */
    private String quizApiKey = "";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** The quiz key when set, otherwise the main key. */
    public String quizKeyOrDefault() {
        return quizApiKey != null && !quizApiKey.isBlank() ? quizApiKey : apiKey;
    }
}
