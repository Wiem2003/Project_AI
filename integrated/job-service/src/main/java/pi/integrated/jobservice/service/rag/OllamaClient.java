package pi.integrated.jobservice.service.rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class OllamaClient {

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public OllamaClient(
            @Value("${rag.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${rag.ollama.model:mistral}") String model,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String getModel() {
        return model;
    }

    public String generateResponse(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.3,
                            "top_p", 0.9
                    )
            );

            String rawResponse = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (rawResponse != null) {
                JsonNode root = objectMapper.readTree(rawResponse);
                if (root.has("response")) {
                    return root.get("response").asText();
                }
            }
            return "Désolé, aucune réponse n'a été générée par le modèle local.";
        } catch (Exception e) {
            log.warn("Impossible de contacter Ollama ({}): {}", model, e.getMessage());
            return "Le serveur de génération locale Ollama n'est pas joignable sur le port 11434. " +
                    "Assurez-vous qu'Ollama est bien lancé localement avec la commande : `ollama run " + model + "`.";
        }
    }
}
