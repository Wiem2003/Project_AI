package pi.integrated.preevaluation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "preevaluation.ai")
public class PreevaluationAiProperties {

    private Provider provider = Provider.GROQ;
    private String apiKey = "";
    private String baseUrl = "";
    private String model = "";

    /** Chat model env: GROQ_MODEL. Embedding model env: GROQ_EMBEDDING_MODEL. */
    private String embeddingModel = "";

    public enum Provider {
        GROQ,
        OPENAI
    }
}
