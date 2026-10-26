package pi.integrated.preevaluation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "preevaluation.rag")
public class PreevaluationRagProperties {

    /** When false, the assistant falls back to prompts without document retrieval. */
    private boolean enabled = true;

    /** Number of passages injected into the LLM prompt. */
    private int topK = 4;

    /** Minimum cosine similarity (0–1) to keep a passage after ranking. */
    private double minScore = 0.12;

    /** Build the in-memory vector index at application startup. */
    private boolean indexOnStartup = true;

    /** Batch size for Groq embedding API calls during indexing. */
    private int embeddingBatchSize = 16;
}
