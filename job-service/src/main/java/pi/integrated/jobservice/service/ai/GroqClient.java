package pi.integrated.jobservice.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GroqClient {

    @Value("${groq.api-key:${GROQ_API_KEY}}")
    private String apiKey;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String model;

    @Value("${groq.fallback-model:openai/gpt-oss-20b}")
    private String fallbackModel;

    @Value("${groq.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GroqCompletionResult {
        private String content;
        private String modelUsed;
        private long latencyMs;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private double estimatedCostUsd;
    }

    /**
     * Envoie une invite au modèle Groq par défaut et retourne le texte.
     */
    public String generateCompletion(String systemPrompt, String userPrompt) {
        return generateDetailedCompletion(model, 0.2, 0.95, 3500, systemPrompt, userPrompt).getContent();
    }

    /**
     * Envoie une invite avec hyperparamètres personnalisés et retourne les métriques complètes.
     */
    public GroqCompletionResult generateDetailedCompletion(String requestedModel, Double temperature, Double topP, Integer maxTokens, String systemPrompt, String userPrompt) {
        String targetModel = (requestedModel != null && !requestedModel.isBlank()) ? requestedModel.trim() : model;
        double temp = temperature != null ? temperature : 0.1;
        double top = topP != null ? topP : 0.95;
        int maxTok = maxTokens != null ? maxTokens : 3500;

        try {
            return callApiDetailed(targetModel, temp, top, maxTok, systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("[GroqClient] Échec avec le modèle {}, tentative avec fallback {}: {}", targetModel, fallbackModel, e.getMessage());
            try {
                return callApiDetailed(fallbackModel, temp, top, maxTok, systemPrompt, userPrompt);
            } catch (Exception ex) {
                log.error("[GroqClient] Échec complet de l'appel Groq: {}", ex.getMessage());
                throw new RuntimeException("Erreur de communication avec l'API Groq: " + ex.getMessage(), ex);
            }
        }
    }

    private GroqCompletionResult callApiDetailed(String modelName, double temperature, double topP, int maxTokens, String systemPrompt, String userPrompt) throws Exception {
        long start = System.currentTimeMillis();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        requestBody.put("temperature", temperature);
        requestBody.put("top_p", topP);
        requestBody.put("max_tokens", maxTokens);

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(groqUrl))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latencyMs = System.currentTimeMillis() - start;

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("Format de réponse Groq inattendu: " + response.body());
            }

            String content = choices.get(0).path("message").path("content").asText();
            JsonNode usage = root.path("usage");
            int promptTok = usage.path("prompt_tokens").asInt(1000);
            int compTok = usage.path("completion_tokens").asInt(500);
            int totalTok = usage.path("total_tokens").asInt(promptTok + compTok);

            // Coût estimé Groq : ~$0.15/1M prompt tokens, ~$0.60/1M completion tokens
            double cost = (promptTok * 0.00000015) + (compTok * 0.00000060);

            return GroqCompletionResult.builder()
                    .content(content)
                    .modelUsed(modelName)
                    .latencyMs(latencyMs)
                    .promptTokens(promptTok)
                    .completionTokens(compTok)
                    .totalTokens(totalTok)
                    .estimatedCostUsd(Math.round(cost * 100000.0) / 100000.0)
                    .build();
        } else {
            throw new RuntimeException("Groq HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    public String getActiveModel() {
        return model;
    }
}
