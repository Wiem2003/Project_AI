package pi.integrated.course.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import pi.integrated.course.config.CourseAiProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Gemini REST client: text/JSON generation (chat + video transcription) and embeddings.
 */
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final int MAX_EMBED_BATCH = 100;
    private static final long MAX_BACKGROUND_WAIT_MS = 65_000L;

    private final CourseAiProperties aiProperties;
    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;

    public record DocumentToEmbed(String title, String text) {
    }

    // ── generation ───────────────────────────────────────────────────────────

    /**
     * Calls models/{model}:generateContent and returns the concatenated text of the first candidate
     * (thought parts are skipped). Fails if the answer was truncated or blocked.
     * Interactive call (a student is waiting): only short retries.
     */
    public String generateContent(String model, Map<String, Object> requestBody) {
        return generateContentWithKey(aiProperties.getApiKey(), model, requestBody);
    }

    /** Same as {@link #generateContent} with another API key (e.g. the quiz generator's own quota). */
    public String generateContentWithKey(String apiKey, String model, Map<String, Object> requestBody) {
        return extractText(post("/models/" + model + ":generateContent", requestBody, false, apiKey));
    }

    /** Same as {@link #generateContent} for background jobs: on HTTP 429 waits the delay Gemini asks for. */
    public String generateContentInBackground(String model, Map<String, Object> requestBody) {
        return extractText(post("/models/" + model + ":generateContent", requestBody, true));
    }

    private static String extractText(JsonNode root) {
        JsonNode candidate = root.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            String blockReason = root.path("promptFeedback").path("blockReason").asText("");
            throw new GeminiException("Gemini returned no candidate"
                    + (blockReason.isBlank() ? "" : " (blocked: " + blockReason + ")"), 0);
        }
        String finishReason = candidate.path("finishReason").asText("");
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new GeminiException("Gemini answer truncated (MAX_TOKENS)", 0);
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            sb.append(part.path("text").asText(""));
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            throw new GeminiException("Empty Gemini answer (finishReason=" + finishReason + ")", 0);
        }
        return text;
    }

    public static Map<String, Object> textPart(String text) {
        return Map.of("text", text);
    }

    /** A public YouTube video part, optionally clipped to [startSeconds, endSeconds]. */
    public static Map<String, Object> youtubePart(String youtubeUrl, Integer startSeconds, Integer endSeconds) {
        Map<String, Object> part = new HashMap<>();
        part.put("fileData", Map.of("fileUri", youtubeUrl));
        if (startSeconds != null || endSeconds != null) {
            Map<String, Object> clip = new HashMap<>();
            if (startSeconds != null) {
                clip.put("startOffset", startSeconds + "s");
            }
            if (endSeconds != null) {
                clip.put("endOffset", endSeconds + "s");
            }
            part.put("videoMetadata", clip);
        }
        return part;
    }

    // ── embeddings ───────────────────────────────────────────────────────────

    /** Embeds passages to index (taskType RETRIEVAL_DOCUMENT), background mode. Vectors are L2-normalized. */
    public List<float[]> embedDocuments(List<DocumentToEmbed> documents) {
        List<Map<String, Object>> requests = new ArrayList<>(documents.size());
        for (DocumentToEmbed doc : documents) {
            Map<String, Object> req = baseEmbedRequest(doc.text(), "RETRIEVAL_DOCUMENT");
            if (doc.title() != null && !doc.title().isBlank()) {
                req.put("title", doc.title());
            }
            requests.add(req);
        }
        return batchEmbed(requests, true);
    }

    /** Embeds a user question (taskType RETRIEVAL_QUERY), interactive mode. */
    public float[] embedQuery(String text) {
        return batchEmbed(List.of(baseEmbedRequest(text, "RETRIEVAL_QUERY")), false).get(0);
    }

    private Map<String, Object> baseEmbedRequest(String text, String taskType) {
        Map<String, Object> req = new HashMap<>();
        req.put("model", "models/" + aiProperties.getEmbeddingModel());
        req.put("content", Map.of("parts", List.of(textPart(text))));
        req.put("taskType", taskType);
        req.put("outputDimensionality", aiProperties.getEmbeddingDimension());
        return req;
    }

    private List<float[]> batchEmbed(List<Map<String, Object>> requests, boolean background) {
        List<float[]> out = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i += MAX_EMBED_BATCH) {
            List<Map<String, Object>> batch = requests.subList(i, Math.min(requests.size(), i + MAX_EMBED_BATCH));
            JsonNode root = post("/models/" + aiProperties.getEmbeddingModel() + ":batchEmbedContents",
                    Map.of("requests", batch), background);
            JsonNode embeddings = root.path("embeddings");
            if (!embeddings.isArray() || embeddings.size() != batch.size()) {
                throw new GeminiException("Unexpected embedding response size", 0);
            }
            for (JsonNode e : embeddings) {
                JsonNode values = e.path("values");
                float[] vec = new float[values.size()];
                for (int k = 0; k < values.size(); k++) {
                    vec[k] = (float) values.get(k).asDouble();
                }
                out.add(normalize(vec));
            }
        }
        return out;
    }

    /** Gemini only normalizes 3072-d embeddings; truncated ones must be normalized client-side. */
    static float[] normalize(float[] vec) {
        double sum = 0.0;
        for (float v : vec) {
            sum += v * v;
        }
        if (sum == 0.0) {
            return vec;
        }
        float inv = (float) (1.0 / Math.sqrt(sum));
        for (int i = 0; i < vec.length; i++) {
            vec[i] *= inv;
        }
        return vec;
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    /**
     * @param background true for ingestion jobs: on 429, wait the retryDelay Gemini returns (free-tier quotas are
     *                   per minute). False for interactive calls: short exponential backoff only.
     */
    private JsonNode post(String path, Object body, boolean background) {
        return post(path, body, background, aiProperties.getApiKey());
    }

    private JsonNode post(String path, Object body, boolean background, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiException("Gemini API key missing: set the GEMINI_API_KEY environment variable", 0);
        }
        final String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new GeminiException("Failed to serialize Gemini request", e);
        }

        String url = trimSlash(aiProperties.getBaseUrl()) + path;
        int attempt = 0;
        while (true) {
            try {
                String response = geminiRestClient.post()
                        .uri(url)
                        .header("x-goog-api-key", apiKey.trim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(jsonBody)
                        .retrieve()
                        .body(String.class);
                return objectMapper.readTree(response);
            } catch (RestClientResponseException e) {
                int code = e.getStatusCode().value();
                boolean retryable = code == 429 || code >= 500;
                Long retryDelay = code == 429 ? parseRetryDelayMillis(e.getResponseBodyAsString()) : null;
                if (retryDelay != null && retryDelay > MAX_BACKGROUND_WAIT_MS) {
                    // e.g. free-tier daily quota: retrying within minutes is pointless
                    log.warn("Gemini quota exhausted on {} — retry possible in {} min", path, retryDelay / 60_000);
                    throw new GeminiException("Gemini quota exhausted (retry possible in "
                            + retryDelay / 60_000 + " min): " + parseProviderError(e.getResponseBodyAsString()), code);
                }
                if (retryable && attempt < aiProperties.getMaxRetries()) {
                    attempt++;
                    if (!background) {
                        retryDelay = null;
                    }
                    sleep(retryDelay != null ? Math.min(retryDelay + 1000, MAX_BACKGROUND_WAIT_MS)
                            : backoffMillis(attempt, background), attempt, code);
                    continue;
                }
                String providerMsg = parseProviderError(e.getResponseBodyAsString());
                log.warn("Gemini HTTP {} on {} — {}", code, path, providerMsg);
                throw new GeminiException("Gemini HTTP " + code + ": " + providerMsg, code);
            } catch (ResourceAccessException e) {
                if (attempt < aiProperties.getMaxRetries()) {
                    attempt++;
                    sleep(backoffMillis(attempt, background), attempt, 0);
                    continue;
                }
                throw new GeminiException("Could not reach Gemini: " + e.getMessage(), e);
            } catch (GeminiException e) {
                throw e;
            } catch (Exception e) {
                throw new GeminiException("Failed to parse Gemini response", e);
            }
        }
    }

    /** Interactive: 1 s, 2 s, 4 s. Background: 15 s, 30 s, 60 s (a per-minute quota window). */
    private static long backoffMillis(int attempt, boolean background) {
        long base = background ? 15_000L : 1_000L;
        return Math.min(base * (1L << (attempt - 1)), MAX_BACKGROUND_WAIT_MS);
    }

    /** Reads google.rpc.RetryInfo "retryDelay": "37s" from a 429 body. */
    Long parseRetryDelayMillis(String body) {
        try {
            for (JsonNode detail : objectMapper.readTree(body).path("error").path("details")) {
                String delay = detail.path("retryDelay").asText("");
                if (delay.endsWith("s")) {
                    return (long) (Double.parseDouble(delay.substring(0, delay.length() - 1)) * 1000);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void sleep(long millis, int attempt, int code) {
        log.info("Gemini call failed (HTTP {}), retry {} in {} ms", code, attempt, millis);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new GeminiException("Interrupted while waiting to retry Gemini call", ie);
        }
    }

    private String parseProviderError(String body) {
        if (body == null || body.isBlank()) {
            return "(no body)";
        }
        try {
            JsonNode msg = objectMapper.readTree(body).path("error").path("message");
            if (msg.isTextual() && !msg.asText().isBlank()) {
                return msg.asText().trim();
            }
        } catch (Exception ignored) {
        }
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }

    private static String trimSlash(String s) {
        String b = s == null ? "" : s.trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }
}
