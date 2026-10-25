package pi.integrated.preevaluation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import pi.integrated.preevaluation.config.PreevaluationAiProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
@RequiredArgsConstructor
public class GroqChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);

    private final PreevaluationAiProperties aiProperties;
    private final RestClient openAiRestClient;
    private final ObjectMapper objectMapper;

    public String chatCompletion(String apiKey, List<Map<String, String>> messages, double temperature, int maxTokens) {
        String base = GroqApiSupport.resolveBaseUrl(aiProperties);
        String model = GroqApiSupport.resolveChatModel(aiProperties);

        List<Map<String, Object>> payloadMessages = new ArrayList<>();
        for (Map<String, String> m : messages) {
            payloadMessages.add(Map.of(
                    "role", m.get("role"),
                    "content", m.get("content")
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("messages", payloadMessages);

        final String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("Failed to serialize AI request body", e);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Internal error preparing AI request.");
        }

        final ResponseEntity<String> response;
        try {
            response = openAiRestClient.post()
                    .uri(base + "/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientResponseException e) {
            String providerMsg = parseProviderErrorMessage(e.getResponseBodyAsString());
            log.warn("AI provider HTTP {} model={} — {}", e.getStatusCode().value(), model,
                    truncateForLog(e.getResponseBodyAsString(), 800));
            String userMsg = AiUserMessageMapper.toUserFriendlyError(providerMsg, e.getStatusCode().value());
            int code = e.getStatusCode().value();
            HttpStatus clientStatus = (code == 401 || code == 403) ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(clientStatus, userMsg);
        } catch (ResourceAccessException e) {
            log.warn("AI provider network error: {}", e.getMessage());
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Could not connect to the AI service. Check your network and try again later.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI provider request failed: {}", e.getMessage());
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Unexpected error calling the AI service. Try again later.");
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI service returned an error. Try again later.");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode message = root.path("choices").path(0).path("message");
            String content = extractAssistantText(message);
            if (content.isBlank()) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Empty reply from AI. Try again.");
            }
            return content.trim();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse AI response: {}", e.getMessage());
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Could not parse AI response. Try again later.");
        }
    }

    private static String extractAssistantText(JsonNode message) {
        if (message.isMissingNode()) {
            return "";
        }
        JsonNode content = message.path("content");
        if (content.isTextual() && !content.asText().isBlank()) {
            return content.asText();
        }
        JsonNode reasoning = message.path("reasoning");
        if (reasoning.isTextual() && !reasoning.asText().isBlank()) {
            return reasoning.asText();
        }
        return "";
    }

    private String parseProviderErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode msg = root.path("error").path("message");
            if (msg.isTextual() && !msg.asText().isBlank()) {
                return msg.asText().trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String truncateForLog(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
