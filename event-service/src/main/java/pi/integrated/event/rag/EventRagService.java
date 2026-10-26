package pi.integrated.event.rag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pi.integrated.event.rag.model.EventRetrievedPassage;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EventRagService {

    private static final Logger log =
            LoggerFactory.getLogger(EventRagService.class);

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}']+");

    /*
     * Topics outside LearnifyEnglish's English-learning scope.
     */
    private static final Set<String> OUT_OF_SCOPE_TOPICS = Set.of(
            "mathematics", "mathematical", "math", "maths",
            "algebra", "calculus", "geometry",
            "physics", "chemistry", "biology",
            "programming", "python", "javascript", "java",
            "coding", "software", "algorithm", "algorithms",
            "database", "databases", "cybersecurity",
            "machine", "robotics", "statistics"
    );

    /*
     * Terms that explicitly identify English-language learning.
     *
     * Avoid generic words such as "learn", "improve", and "study"
     * here, because they would incorrectly classify "learn Python"
     * as an English-learning request.
     */
    private static final Set<String> ENGLISH_LEARNING_TERMS = Set.of(
            "english", "anglais", "anglaise", "anglaises",
            "grammar", "grammatical", "grammaire",
            "vocabulary", "vocabulaire",
            "speaking", "speak", "fluent", "fluency", "fluently",
            "listening", "writing", "reading",
            "pronunciation", "prononciation",
            "ielts", "toefl", "conversation",
            "language", "langue",
            "tense", "tenses", "verb", "verbs",
            "conjugation", "syntax", "essay", "essays"
    );

    private final EventRagRetrievalService retrievalService;
    private final GeminiClient geminiClient;
    private final EventVectorIndex vectorIndex;
    private final RagResponseCache responseCache;
    private final RagPerformanceTracker performanceTracker;

    /**
     * Generates event recommendations using retrieval + Gemini.
     */
    public Map<String, Object> recommend(
            String query,
            String category) {

        String safeQuery = query == null ? "" : query.trim();

        if (safeQuery.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "The query must not be empty.");
            return error;
        }

        log.info(
                "RAG recommendation request: query='{}', category='{}'",
                safeQuery,
                category
        );

        long startTotal = System.currentTimeMillis();

        /*
         * 1. Check the response cache.
         * Always return a copy to avoid modifying the cached Map.
         */
        String cacheKey = responseCache.cacheKey(
                safeQuery,
                category
        );

        Map<String, Object> cached = responseCache.get(cacheKey);

        if (cached != null) {

            Map<String, Object> cachedCopy =
                    new LinkedHashMap<>(cached);

            long elapsed =
                    System.currentTimeMillis() - startTotal;

            cachedCopy.put("fromCache", true);
            cachedCopy.put("responseTimeMs", elapsed);

            performanceTracker.recordRequest(
                    new RagPerformanceTracker.RequestMetrics(
                            safeQuery,
                            0,
                            0,
                            elapsed,
                            0,
                            true,
                            0
                    )
            );

            return cachedCopy;
        }

        /*
         * 2. Check whether the request is outside the platform scope.
         */
        boolean outOfScope = isClearlyOutOfScope(safeQuery);

        List<EventRetrievedPassage> passages = List.of();

        long retrievalTime = 0;

        /*
         * 3. Retrieve only when the request is in scope.
         */
        if (!outOfScope) {

            long startRetrieval = System.currentTimeMillis();

            passages = retrievalService.retrieve(
                    safeQuery,
                    category
            );

            retrievalTime =
                    System.currentTimeMillis() - startRetrieval;
        }

        log.info(
                "Retrieved {} relevant passages in {}ms",
                passages.size(),
                retrievalTime
        );

        /*
         * 4. Build the response.
         *
         * Out-of-scope requests do not need a Gemini call.
         */
        String recommendation;
        long llmTime = 0;
        int estimatedTokens = 0;

        if (outOfScope) {

            recommendation =
                    "LearnifyEnglish is dedicated to English-language "
                            + "learning. The current platform does not specialize "
                            + "in the subject you requested. You can ask me about "
                            + "English grammar, vocabulary, speaking, listening, "
                            + "writing, or English-language exams.";

        } else if (passages.isEmpty()) {

            recommendation =
                    "I could not find a sufficiently relevant event "
                            + "in the current LearnifyEnglish catalog. "
                            + "Try describing the English skill you want "
                            + "to improve, for example grammar, speaking, "
                            + "vocabulary, or IELTS preparation.";

        } else {

            String systemPrompt =
                    EventRagPromptTemplates
                            .systemPromptForRecommendation();

            String userPrompt =
                    EventRagPromptTemplates
                            .userPromptForRecommendation(
                                    safeQuery,
                                    category,
                                    passages
                            );

            try {

                long startLlm = System.currentTimeMillis();

                recommendation = geminiClient.chat(
                        systemPrompt,
                        userPrompt
                );

                llmTime =
                        System.currentTimeMillis() - startLlm;

                estimatedTokens =
                        RagPerformanceTracker.estimateTokens(
                                systemPrompt
                                        + userPrompt
                                        + recommendation
                        );

            } catch (Exception exception) {

                log.error(
                        "Gemini recommendation failed",
                        exception
                );

                recommendation =
                        "The recommendation service is temporarily "
                                + "unavailable. Please try again later.";
            }
        }

        /*
         * 5. Build the response and source metadata.
         */
        long totalTime =
                System.currentTimeMillis() - startTotal;

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("recommendation", recommendation);
        result.put("query", safeQuery);
        result.put("category", category);
        result.put("sourcesUsed", passages.size());
        result.put("sources", buildSources(passages));
        result.put("outOfScope", outOfScope);
        result.put("fromCache", false);
        result.put("responseTimeMs", totalTime);

        /*
         * 6. Store a copy in the cache.
         */
        responseCache.put(
                cacheKey,
                new LinkedHashMap<>(result)
        );

        /*
         * 7. Record performance metrics.
         */
        performanceTracker.recordRequest(
                new RagPerformanceTracker.RequestMetrics(
                        safeQuery,
                        retrievalTime,
                        llmTime,
                        totalTime,
                        passages.size(),
                        false,
                        estimatedTokens
                )
        );

        return result;
    }

    /**
     * Builds source metadata without using Map.of(), which rejects nulls.
     */
    private static List<Map<String, Object>> buildSources(
            List<EventRetrievedPassage> passages) {

        List<Map<String, Object>> sources = new ArrayList<>();

        for (EventRetrievedPassage passage : passages) {

            Map<String, Object> source = new LinkedHashMap<>();

            source.put("id", passage.getDocumentId());
            source.put("title", passage.getTitle());
            source.put("category", passage.getCategory());
            source.put("score", passage.getScore());

            sources.add(source);
        }

        return sources;
    }

    /**
     * Lightweight scope check.
     *
     * This is a rule-based guard, not a full semantic classifier.
     */
    private static boolean isClearlyOutOfScope(String query) {

        Set<String> tokens = tokenize(query);

        boolean mentionsEnglishLearning = tokens.stream()
                .anyMatch(ENGLISH_LEARNING_TERMS::contains);

        /*
         * An explicit English-learning term means the request can
         * be handled by the English-learning recommendation pipeline.
         */
        if (mentionsEnglishLearning) {
            return false;
        }

        return tokens.stream()
                .anyMatch(OUT_OF_SCOPE_TOPICS::contains);
    }

    private static Set<String> tokenize(String text) {

        if (text == null || text.isBlank()) {
            return Set.of();
        }

        String normalized = Normalizer.normalize(
                text.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "");

        Set<String> tokens = new HashSet<>();

        Matcher matcher = TOKEN_PATTERN.matcher(normalized);

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return tokens;
    }

    /**
     * Returns the current RAG index status.
     */
    public Map<String, Object> getStatus() {

        Map<String, Object> status = new LinkedHashMap<>();

        status.put("ready", vectorIndex.isReady());
        status.put("totalChunks", vectorIndex.size());

        status.put(
                "message",
                vectorIndex.isReady()
                        ? "RAG system is ready with "
                        + vectorIndex.size()
                        + " indexed event chunks."
                        : "RAG system is not yet initialized."
        );

        return status;
    }

    /**
     * Analyzes event trends using the indexed catalog.
     */
    public Map<String, Object> analyzeTrends(String query) {

        String safeQuery = query == null ? "" : query.trim();

        if (safeQuery.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "The query must not be empty.");
            return error;
        }

        log.info(
                "RAG trends analysis request: query='{}'",
                safeQuery
        );

        long startTotal = System.currentTimeMillis();

        long startRetrieval = System.currentTimeMillis();

        List<EventRetrievedPassage> passages =
                retrievalService.retrieve(safeQuery, null);

        long retrievalTime =
                System.currentTimeMillis() - startRetrieval;

        String analysis;
        long llmTime = 0;
        int estimatedTokens = 0;

        if (passages.isEmpty()) {

            analysis =
                    "There is not enough relevant event data "
                            + "in the current catalog to answer this question.";

        } else {

            String systemPrompt =
                    EventRagPromptTemplates.systemPromptForAnalysis();

            String userPrompt =
                    "User question:\n"
                            + safeQuery
                            + "\n\nRetrieved event data:\n"
                            + EventRagPromptTemplates
                            .formatRetrievedContext(passages);

            try {

                long startLlm = System.currentTimeMillis();

                analysis = geminiClient.chat(
                        systemPrompt,
                        userPrompt
                );

                llmTime =
                        System.currentTimeMillis() - startLlm;

                estimatedTokens =
                        RagPerformanceTracker.estimateTokens(
                                systemPrompt
                                        + userPrompt
                                        + analysis
                        );

            } catch (Exception exception) {

                log.error(
                        "Gemini trends analysis failed",
                        exception
                );

                analysis =
                        "The analysis service is temporarily "
                                + "unavailable. Please try again later.";
            }
        }

        long totalTime =
                System.currentTimeMillis() - startTotal;

        performanceTracker.recordRequest(
                new RagPerformanceTracker.RequestMetrics(
                        safeQuery,
                        retrievalTime,
                        llmTime,
                        totalTime,
                        passages.size(),
                        false,
                        estimatedTokens
                )
        );

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("analysis", analysis);
        result.put("query", safeQuery);
        result.put("dataPointsAnalyzed", passages.size());
        result.put("responseTimeMs", totalTime);

        return result;
    }

    /**
     * Returns performance and cache statistics.
     */
    public Map<String, Object> getPerformanceMetrics() {

        Map<String, Object> metrics = new LinkedHashMap<>();

        metrics.put(
                "performance",
                performanceTracker.getPerformanceReport()
        );

        metrics.put("cache", responseCache.getStats());

        Map<String, Object> index = new LinkedHashMap<>();

        index.put("totalChunks", vectorIndex.size());
        index.put("ready", vectorIndex.isReady());

        metrics.put("index", index);

        return metrics;
    }
}