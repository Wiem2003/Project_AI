package pi.integrated.event.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracker de performance pour le pipeline RAG.
 * Mesure la latence, le nombre de tokens, et les métriques de qualité.
 *
 * Techniques d'optimisation documentées :
 * - Suivi de la latence de chaque étape (retrieval, LLM, total)
 * - Estimation du coût en tokens
 * - Métriques de qualité (nombre de sources, scores de pertinence)
 */
@Component
public class RagPerformanceTracker {

    private static final Logger log = LoggerFactory.getLogger(RagPerformanceTracker.class);

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalCacheHits = new AtomicLong(0);
    private final AtomicLong totalLlmCalls = new AtomicLong(0);
    private final AtomicLong totalTokensEstimated = new AtomicLong(0);

    /** Garde les 50 dernières métriques pour les statistiques */
    private final ConcurrentLinkedQueue<RequestMetrics> recentMetrics = new ConcurrentLinkedQueue<>();
    private static final int MAX_RECENT = 50;

    /**
     * Enregistre les métriques d'une requête RAG.
     */
    public void recordRequest(RequestMetrics metrics) {
        totalRequests.incrementAndGet();
        if (metrics.isCacheHit()) {
            totalCacheHits.incrementAndGet();
        } else {
            totalLlmCalls.incrementAndGet();
            totalTokensEstimated.addAndGet(metrics.getEstimatedTokens());
        }

        recentMetrics.add(metrics);
        while (recentMetrics.size() > MAX_RECENT) {
            recentMetrics.poll();
        }

        log.info("RAG Metrics — Total: {}ms, Retrieval: {}ms, LLM: {}ms, Sources: {}, Cache: {}, Tokens: ~{}",
                metrics.getTotalTimeMs(), metrics.getRetrievalTimeMs(), metrics.getLlmTimeMs(),
                metrics.getSourceCount(), metrics.isCacheHit(), metrics.getEstimatedTokens());
    }

    /**
     * Retourne un rapport complet des performances.
     */
    public Map<String, Object> getPerformanceReport() {
        List<RequestMetrics> recent = new ArrayList<>(recentMetrics);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalRequests", totalRequests.get());
        report.put("totalCacheHits", totalCacheHits.get());
        report.put("totalLlmCalls", totalLlmCalls.get());
        report.put("cacheHitRate", totalRequests.get() > 0
                ? String.format(java.util.Locale.US, "%.1f%%", (totalCacheHits.get() * 100.0) / totalRequests.get())
                : "0.0%");
        report.put("totalTokensEstimated", totalTokensEstimated.get());
        report.put("estimatedCostUSD", String.format(java.util.Locale.US, "$%.4f", totalTokensEstimated.get() * 0.000001)); // ~$1/M tokens

        // Statistiques de latence
        if (!recent.isEmpty()) {
            DoubleSummaryStatistics totalStats = recent.stream()
                    .mapToDouble(RequestMetrics::getTotalTimeMs).summaryStatistics();
            DoubleSummaryStatistics retrievalStats = recent.stream()
                    .mapToDouble(RequestMetrics::getRetrievalTimeMs).summaryStatistics();
            DoubleSummaryStatistics llmStats = recent.stream()
                    .filter(m -> !m.isCacheHit())
                    .mapToDouble(RequestMetrics::getLlmTimeMs).summaryStatistics();

            report.put("latency", Map.of(
                    "totalAvgMs", String.format(java.util.Locale.US, "%.0f", totalStats.getAverage()),
                    "totalMaxMs", String.format(java.util.Locale.US, "%.0f", totalStats.getMax()),
                    "totalMinMs", String.format(java.util.Locale.US, "%.0f", totalStats.getMin()),
                    "retrievalAvgMs", String.format(java.util.Locale.US, "%.0f", retrievalStats.getAverage()),
                    "llmAvgMs", llmStats.getCount() > 0
                            ? String.format(java.util.Locale.US, "%.0f", llmStats.getAverage()) : "N/A"
            ));
        }

        // Configuration actuelle
        report.put("configuration", Map.of(
                "model", "gemini-2.5-flash",
                "temperature", 0.7,
                "maxOutputTokens", 2048,
                "embeddingDimension", 384,
                "embeddingType", "LocalHash (no API cost)"
        ));

        return report;
    }

    /**
     * Estime le nombre de tokens pour un texte.
     * Approximation : 1 token ≈ 4 caractères en anglais/français.
     */
    public static int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }

    /**
     * Métriques d'une requête RAG individuelle.
     */
    public static class RequestMetrics {
        private final long retrievalTimeMs;
        private final long llmTimeMs;
        private final long totalTimeMs;
        private final int sourceCount;
        private final boolean cacheHit;
        private final int estimatedTokens;
        private final String query;

        public RequestMetrics(String query, long retrievalTimeMs, long llmTimeMs,
                              long totalTimeMs, int sourceCount, boolean cacheHit,
                              int estimatedTokens) {
            this.query = query;
            this.retrievalTimeMs = retrievalTimeMs;
            this.llmTimeMs = llmTimeMs;
            this.totalTimeMs = totalTimeMs;
            this.sourceCount = sourceCount;
            this.cacheHit = cacheHit;
            this.estimatedTokens = estimatedTokens;
        }

        public long getRetrievalTimeMs() { return retrievalTimeMs; }
        public long getLlmTimeMs() { return llmTimeMs; }
        public long getTotalTimeMs() { return totalTimeMs; }
        public int getSourceCount() { return sourceCount; }
        public boolean isCacheHit() { return cacheHit; }
        public int getEstimatedTokens() { return estimatedTokens; }
        public String getQuery() { return query; }
    }
}
