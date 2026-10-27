package pi.integrated.preevaluation.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import pi.integrated.preevaluation.ai.GroqApiSupport;
import pi.integrated.preevaluation.config.PreevaluationAiProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GroqEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(GroqEmbeddingClient.class);

    private final PreevaluationAiProperties aiProperties;
    private final RestClient openAiRestClient;
    private final ObjectMapper objectMapper;
    private final LocalHashEmbeddingService localHashEmbeddingService;

    private volatile Boolean groqEmbeddingsAvailable;

    public boolean isGroqEmbeddingsAvailable(String apiKey) {
        if (groqEmbeddingsAvailable != null) {
            return groqEmbeddingsAvailable;
        }
        synchronized (this) {
            if (groqEmbeddingsAvailable != null) {
                return groqEmbeddingsAvailable;
            }
            try {
                embedBatchWithGroq(apiKey, List.of("healthcheck"));
                groqEmbeddingsAvailable = true;
            } catch (Exception e) {
                log.warn("Groq embeddings API unavailable — using local hash embeddings for RAG index. Cause: {}",
                        e.getMessage());
                groqEmbeddingsAvailable = false;
            }
            return groqEmbeddingsAvailable;
        }
    }

    public List<float[]> embedTexts(String apiKey, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (apiKey != null && !apiKey.isBlank() && isGroqEmbeddingsAvailable(apiKey.trim())) {
            return embedBatchWithGroq(apiKey.trim(), texts);
        }
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(localHashEmbeddingService.embed(t));
        }
        return out;
    }

    private List<float[]> embedBatchWithGroq(String apiKey, List<String> texts) {
        String base = GroqApiSupport.resolveBaseUrl(aiProperties);
        String model = GroqApiSupport.resolveEmbeddingModel(aiProperties);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", texts);
        body.put("encoding_format", "float");

        final String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize embedding request", e);
        }

        try {
            String response = openAiRestClient.post()
                    .uri(base + "/v1/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("Empty embedding response");
            }
            List<float[]> vectors = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode emb = item.path("embedding");
                if (!emb.isArray()) {
                    throw new IllegalStateException("Missing embedding vector");
                }
                float[] vec = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) {
                    vec[i] = (float) emb.get(i).asDouble();
                }
                vectors.add(vec);
            }
            return vectors;
        } catch (RestClientResponseException e) {
            groqEmbeddingsAvailable = false;
            throw e;
        } catch (Exception e) {
            groqEmbeddingsAvailable = false;
            throw new IllegalStateException("Groq embedding call failed", e);
        }
    }
}
