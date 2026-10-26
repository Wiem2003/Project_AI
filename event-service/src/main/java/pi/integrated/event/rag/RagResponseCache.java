package pi.integrated.event.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache LRU simple pour les réponses RAG.
 * Réduit la latence et la consommation de tokens pour les requêtes répétées.
 *
 * Technique d'optimisation : Caching des réponses LLM
 * - Réduit le coût API (moins d'appels Gemini)
 * - Réduit la latence (réponse instantanée pour les requêtes en cache)
 * - Réduit la consommation de tokens
 */
@Component
public class RagResponseCache {

    private static final Logger log = LoggerFactory.getLogger(RagResponseCache.class);

    /** Durée de vie d'une entrée de cache en secondes (5 minutes) */
    private static final long TTL_SECONDS = 300;

    /** Taille maximale du cache */
    private static final int MAX_SIZE = 100;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Cherche une réponse en cache pour la clé donnée.
     *
     * @param key Clé de cache (ex: hash de la requête + catégorie)
     * @return La réponse en cache, ou null si absente ou expirée
     */
    public Map<String, Object> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            log.debug("Cache MISS for key: {}", key);
            return null;
        }
        if (entry.isExpired()) {
            log.debug("Cache EXPIRED for key: {}", key);
            cache.remove(key);
            return null;
        }
        entry.incrementHits();
        log.info("Cache HIT for key: {} (hits: {})", key, entry.getHits());
        return entry.getResponse();
    }

    /**
     * Stocke une réponse dans le cache.
     *
     * @param key      Clé de cache
     * @param response La réponse à stocker
     */
    public void put(String key, Map<String, Object> response) {
        // Éviction simple si le cache est plein
        if (cache.size() >= MAX_SIZE) {
            evictOldest();
        }
        cache.put(key, new CacheEntry(response));
        log.info("Cache PUT for key: {} (total entries: {})", key, cache.size());
    }

    /**
     * Génère une clé de cache à partir de la requête et de la catégorie.
     */
    public String cacheKey(String query, String category) {
        String normalized = (query == null ? "" : query.trim().toLowerCase())
                + "|" + (category == null ? "" : category.trim().toUpperCase());
        return String.valueOf(normalized.hashCode());
    }

    /**
     * Retourne les statistiques du cache.
     */
    public Map<String, Object> getStats() {
        long totalHits = cache.values().stream().mapToLong(CacheEntry::getHits).sum();
        long expiredCount = cache.values().stream().filter(CacheEntry::isExpired).count();
        return Map.of(
                "totalEntries", cache.size(),
                "maxSize", MAX_SIZE,
                "ttlSeconds", TTL_SECONDS,
                "totalHits", totalHits,
                "expiredEntries", expiredCount
        );
    }

    /**
     * Vide tout le cache.
     */
    public void clear() {
        cache.clear();
        log.info("Cache cleared");
    }

    /**
     * Supprime l'entrée la plus ancienne.
     */
    private void evictOldest() {
        String oldestKey = null;
        Instant oldestTime = Instant.MAX;
        for (Map.Entry<String, CacheEntry> e : cache.entrySet()) {
            if (e.getValue().getCreatedAt().isBefore(oldestTime)) {
                oldestTime = e.getValue().getCreatedAt();
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            cache.remove(oldestKey);
            log.info("Cache evicted oldest entry: {}", oldestKey);
        }
    }

    /**
     * Entrée de cache avec TTL et compteur de hits.
     */
    private static class CacheEntry {
        private final Map<String, Object> response;
        private final Instant createdAt;
        private long hits;

        CacheEntry(Map<String, Object> response) {
            this.response = response;
            this.createdAt = Instant.now();
            this.hits = 0;
        }

        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(TTL_SECONDS));
        }

        Map<String, Object> getResponse() { return response; }
        Instant getCreatedAt() { return createdAt; }
        long getHits() { return hits; }
        void incrementHits() { this.hits++; }
    }
}
