package pi.integrated.event.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller REST pour les fonctionnalités RAG + LLM des événements.
 * Expose les endpoints de recommandation intelligente et d'analyse.
 */
@RestController
@RequestMapping("/api/events/rag")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EventRagController {

    private final EventRagService ragService;

    /**
     * POST /api/events/rag/recommend
     * Recommandation intelligente d'événements basée sur RAG + LLM.
     *
     * Body exemple : { "query": "je veux améliorer mon business english", "category": "BUSINESS_ENGLISH" }
     */
    @PostMapping("/recommend")
    public ResponseEntity<Map<String, Object>> recommend(@RequestBody Map<String, String> request) {
        String query = request.getOrDefault("query", "");
        String category = request.getOrDefault("category", null);

        if (query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Le champ 'query' est obligatoire",
                    "example", Map.of(
                            "query", "je veux améliorer mon anglais pour les entretiens",
                            "category", "BUSINESS_ENGLISH"
                    )
            ));
        }

        Map<String, Object> result = ragService.recommend(query, category);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/events/rag/status
     * Vérifie le statut du système RAG.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(ragService.getStatus());
    }

    /**
     * POST /api/events/rag/analyze
     * Analyse les tendances des événements avec le LLM.
     *
     * Body exemple : { "query": "quels types d'événements sont les plus populaires ?" }
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, String> request) {
        String query = request.getOrDefault("query", "");
        if (query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Le champ 'query' est obligatoire"
            ));
        }
        Map<String, Object> result = ragService.analyzeTrends(query);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/events/rag/metrics
     * Retourne les métriques de performance du pipeline RAG.
     * Utile pour le rapport : latence, tokens, cache hits, coût estimé.
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(ragService.getPerformanceMetrics());
    }
}
