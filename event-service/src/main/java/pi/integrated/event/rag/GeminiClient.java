package pi.integrated.event.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client HTTP pour l'API Google Gemini.
 * Envoie des requêtes au LLM et retourne les réponses générées.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    public GeminiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Envoie un prompt au LLM Gemini et retourne la réponse générée.
     *
     * @param systemPrompt Instructions système pour le LLM
     * @param userMessage  Le message/question de l'utilisateur
     * @return La réponse textuelle générée par Gemini
     */
    public String chat(String systemPrompt, String userMessage) {
        try {
            String fullPrompt = systemPrompt + "\n\n" + userMessage;

            // Construction du body de la requête (format Gemini API)
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", fullPrompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));

            requestBody.put("contents", List.of(content));

            // Configuration de génération
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 2048);
            requestBody.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Appel à l'API Gemini avec la clé en query parameter
            String url = apiUrl + "?key=" + apiKey;

            log.info("Appel à Gemini API: {}", apiUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            log.info("Réponse Gemini reçue avec status: {}", response.getStatusCode());

            // Extraction du texte de la réponse
            JsonNode root = objectMapper.readTree(response.getBody());
            String result = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            log.info("Texte extrait de la réponse Gemini (longueur: {})", result.length());
            return result;

        } catch (HttpClientErrorException e) {
            log.error("Erreur HTTP Gemini API: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Erreur API Gemini (code " + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Erreur lors de l'appel à Gemini API", e);
            throw new RuntimeException("Erreur lors de l'appel à Gemini API: " + e.getMessage(), e);
        }
    }
}
