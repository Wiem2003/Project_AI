package pi.integrated.event.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pi.integrated.event.rag.model.EventIndexedChunk;
import pi.integrated.event.rag.model.EventKnowledgeDocument;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour le pipeline RAG.
 * Vérifie le bon fonctionnement des composants clés :
 * - Embedding local (hash-based)
 * - Construction des chunks
 * - Index vectoriel
 * - Cache des réponses
 * - Similarité cosinus
 *
 * Ces tests ont été conçus pour valider le pipeline RAG
 * sans dépendre d'API externes (Gemini).
 */
class EventRagPipelineTest {

    private LocalHashEmbeddingService embeddingService;
    private EventChunkBuilder chunkBuilder;
    private EventVectorIndex vectorIndex;
    private RagResponseCache responseCache;

    @BeforeEach
    void setUp() {
        embeddingService = new LocalHashEmbeddingService();
        chunkBuilder = new EventChunkBuilder();
        vectorIndex = new EventVectorIndex();
        responseCache = new RagResponseCache();
    }

    // ============================
    // Embedding Tests
    // ============================

    @Test
    @DisplayName("Embedding should produce a vector of 384 dimensions")
    void testEmbeddingDimension() {
        float[] vec = embeddingService.embed("Learn English speaking skills");
        assertEquals(384, vec.length, "Embedding dimension should be 384");
    }

    @Test
    @DisplayName("Embedding of null/blank text should return zero vector")
    void testEmbeddingNullOrBlank() {
        float[] vecNull = embeddingService.embed(null);
        float[] vecBlank = embeddingService.embed("   ");

        assertEquals(384, vecNull.length);
        assertEquals(384, vecBlank.length);
        for (int i = 0; i < 384; i++) {
            assertEquals(0.0f, vecNull[i], "Null embedding should be all zeros");
            assertEquals(0.0f, vecBlank[i], "Blank embedding should be all zeros");
        }
    }

    @Test
    @DisplayName("Same text should produce identical embeddings (deterministic)")
    void testEmbeddingDeterminism() {
        float[] vec1 = embeddingService.embed("Business English workshop");
        float[] vec2 = embeddingService.embed("Business English workshop");

        assertArrayEquals(vec1, vec2, "Same text should produce identical embeddings");
    }

    @Test
    @DisplayName("Similar texts should have higher cosine similarity than unrelated texts")
    void testCosineSimilarityRelevance() {
        float[] business = embeddingService.embed("business English negotiation deal");
        float[] interview = embeddingService.embed("business English interview job");
        float[] cooking = embeddingService.embed("chocolate cake recipe baking oven");

        double simBusinessInterview = LocalHashEmbeddingService.cosineSimilarity(business, interview);
        double simBusinessCooking = LocalHashEmbeddingService.cosineSimilarity(business, cooking);

        assertTrue(simBusinessInterview > simBusinessCooking,
                "Business-interview similarity (" + simBusinessInterview +
                ") should be higher than business-cooking (" + simBusinessCooking + ")");
    }

    @Test
    @DisplayName("Cosine similarity of identical vectors should be ~1.0")
    void testCosineSimilarityIdentical() {
        float[] vec = embeddingService.embed("IELTS preparation course");
        double sim = LocalHashEmbeddingService.cosineSimilarity(vec, vec);
        assertEquals(1.0, sim, 0.001, "Cosine similarity of identical vectors should be ~1.0");
    }

    @Test
    @DisplayName("Cosine similarity with null/zero vector should be 0.0")
    void testCosineSimilarityNullVector() {
        float[] vec = embeddingService.embed("English grammar");
        float[] zero = new float[384];

        assertEquals(0.0, LocalHashEmbeddingService.cosineSimilarity(vec, zero));
        assertEquals(0.0, LocalHashEmbeddingService.cosineSimilarity(null, vec));
        assertEquals(0.0, LocalHashEmbeddingService.cosineSimilarity(vec, null));
    }

    // ============================
    // Chunk Builder Tests
    // ============================

    @Test
    @DisplayName("ChunkBuilder should produce valid chunks from a document")
    void testChunkBuilder() {
        EventKnowledgeDocument doc = EventKnowledgeDocument.builder()
                .id("test-001")
                .title("Business Writing Workshop")
                .category("BUSINESS_ENGLISH")
                .description("Learn to write professional emails")
                .targetAudience("Business professionals (B1-B2)")
                .skills(List.of("writing", "email", "professional tone"))
                .duration("2 hours")
                .format("Online")
                .typicalFeedback("Very practical workshop")
                .keywords(List.of("business", "writing", "email", "professional"))
                .build();

        EventIndexedChunk chunk = chunkBuilder.toChunk(doc);

        assertEquals("test-001#0", chunk.getChunkId());
        assertEquals("test-001", chunk.getDocumentId());
        assertEquals("Business Writing Workshop", chunk.getTitle());
        assertEquals("BUSINESS_ENGLISH", chunk.getCategory());
        assertTrue(chunk.getPassageText().contains("Business Writing Workshop"));
        assertTrue(chunk.getPassageText().contains("professional emails"));
        assertTrue(chunk.getSearchableText().contains("writing"));
        assertTrue(chunk.getSearchableText().contains("email"));
        assertEquals(3, chunk.getSkills().size());
    }

    @Test
    @DisplayName("ChunkBuilder should handle documents with null fields")
    void testChunkBuilderNullFields() {
        EventKnowledgeDocument doc = EventKnowledgeDocument.builder()
                .id("test-null")
                .title("Test Event")
                .build();

        EventIndexedChunk chunk = chunkBuilder.toChunk(doc);
        assertNotNull(chunk);
        assertEquals("test-null#0", chunk.getChunkId());
        assertNotNull(chunk.getPassageText());
        assertNotNull(chunk.getSearchableText());
    }

    @Test
    @DisplayName("ChunkBuilder should convert multiple documents")
    void testChunkBuilderMultiple() {
        List<EventKnowledgeDocument> docs = List.of(
                EventKnowledgeDocument.builder().id("a").title("Event A").build(),
                EventKnowledgeDocument.builder().id("b").title("Event B").build(),
                EventKnowledgeDocument.builder().id("c").title("Event C").build()
        );

        List<EventIndexedChunk> chunks = chunkBuilder.toChunks(docs);
        assertEquals(3, chunks.size());
        assertEquals("a#0", chunks.get(0).getChunkId());
        assertEquals("b#0", chunks.get(1).getChunkId());
        assertEquals("c#0", chunks.get(2).getChunkId());
    }

    // ============================
    // Vector Index Tests
    // ============================

    @Test
    @DisplayName("Vector index should start empty and not ready")
    void testVectorIndexEmpty() {
        assertFalse(vectorIndex.isReady());
        assertEquals(0, vectorIndex.size());
    }

    @Test
    @DisplayName("Vector index should store and retrieve chunks")
    void testVectorIndexStoreRetrieve() {
        EventIndexedChunk chunk1 = EventIndexedChunk.builder()
                .chunkId("c1").documentId("d1").title("Event 1")
                .embedding(embeddingService.embed("business english"))
                .build();
        EventIndexedChunk chunk2 = EventIndexedChunk.builder()
                .chunkId("c2").documentId("d2").title("Event 2")
                .embedding(embeddingService.embed("ielts preparation"))
                .build();

        vectorIndex.replaceAll(List.of(chunk1, chunk2));

        assertTrue(vectorIndex.isReady());
        assertEquals(2, vectorIndex.size());
        assertEquals(2, vectorIndex.snapshot().size());
    }

    @Test
    @DisplayName("Vector index clear should remove all chunks")
    void testVectorIndexClear() {
        vectorIndex.replaceAll(List.of(
                EventIndexedChunk.builder().chunkId("c1").build()
        ));
        assertTrue(vectorIndex.isReady());

        vectorIndex.clear();
        assertFalse(vectorIndex.isReady());
        assertEquals(0, vectorIndex.size());
    }

    // ============================
    // Cache Tests
    // ============================

    @Test
    @DisplayName("Cache should return null for unknown keys (cache miss)")
    void testCacheMiss() {
        assertNull(responseCache.get("unknown-key"));
    }

    @Test
    @DisplayName("Cache should store and retrieve responses (cache hit)")
    void testCacheHit() {
        var response = java.util.Map.of("recommendation", (Object) "Test recommendation");
        responseCache.put("test-key", response);

        var cached = responseCache.get("test-key");
        assertNotNull(cached);
        assertEquals("Test recommendation", cached.get("recommendation"));
    }

    @Test
    @DisplayName("Cache key should be deterministic for same query+category")
    void testCacheKeyDeterminism() {
        String key1 = responseCache.cacheKey("business english", "WORKSHOP");
        String key2 = responseCache.cacheKey("business english", "WORKSHOP");
        assertEquals(key1, key2);
    }

    @Test
    @DisplayName("Cache key should differ for different queries")
    void testCacheKeyDifferent() {
        String key1 = responseCache.cacheKey("business english", "WORKSHOP");
        String key2 = responseCache.cacheKey("ielts preparation", "EXAM");
        assertNotEquals(key1, key2);
    }

    @Test
    @DisplayName("Cache clear should remove all entries")
    void testCacheClear() {
        responseCache.put("k1", java.util.Map.of("a", (Object) "1"));
        responseCache.put("k2", java.util.Map.of("b", (Object) "2"));
        responseCache.clear();

        assertNull(responseCache.get("k1"));
        assertNull(responseCache.get("k2"));
    }

    @Test
    @DisplayName("Cache stats should report correct values")
    void testCacheStats() {
        responseCache.put("k1", java.util.Map.of("a", (Object) "1"));
        responseCache.get("k1"); // hit
        responseCache.get("k1"); // hit

        var stats = responseCache.getStats();
        assertEquals(1, stats.get("totalEntries"));
        assertEquals(2L, stats.get("totalHits"));
    }

    // ============================
    // Performance Tracker Tests
    // ============================

    @Test
    @DisplayName("Performance tracker should estimate tokens correctly")
    void testTokenEstimation() {
        // ~4 chars per token
        assertEquals(0, RagPerformanceTracker.estimateTokens(null));
        assertEquals(1, RagPerformanceTracker.estimateTokens("Hi"));
        assertTrue(RagPerformanceTracker.estimateTokens("This is a longer text for testing") > 5);
    }

    @Test
    @DisplayName("Performance tracker should record metrics and produce report")
    void testPerformanceReport() {
        RagPerformanceTracker tracker = new RagPerformanceTracker();

        tracker.recordRequest(new RagPerformanceTracker.RequestMetrics(
                "test query", 10, 500, 520, 3, false, 1000));
        tracker.recordRequest(new RagPerformanceTracker.RequestMetrics(
                "cached query", 0, 0, 5, 0, true, 0));

        var report = tracker.getPerformanceReport();
        assertEquals(2L, report.get("totalRequests"));
        assertEquals(1L, report.get("totalCacheHits"));
        assertEquals(1L, report.get("totalLlmCalls"));
        assertEquals("50.0%", report.get("cacheHitRate"));
        assertEquals(1000L, report.get("totalTokensEstimated"));
    }

    // ============================
    // Prompt Templates Tests
    // ============================

    @Test
    @DisplayName("Prompt templates should generate non-empty prompts")
    void testPromptTemplatesNotEmpty() {
        String systemRec = EventRagPromptTemplates.systemPromptForRecommendation();
        String systemAnalysis = EventRagPromptTemplates.systemPromptForAnalysis();

        assertNotNull(systemRec);
        assertFalse(systemRec.isBlank());
        assertTrue(systemRec.contains("LearnifyEnglish"));

        assertNotNull(systemAnalysis);
        assertFalse(systemAnalysis.isBlank());
        assertTrue(systemAnalysis.contains("analytics"));
    }

    @Test
    @DisplayName("formatRetrievedContext should handle empty passages")
    void testFormatEmptyPassages() {
        String result = EventRagPromptTemplates.formatRetrievedContext(List.of());
        assertTrue(result.contains("No matching events"));
    }

    @Test
    @DisplayName("formatRetrievedContext should format passages correctly")
    void testFormatPassages() {
        var passages = List.of(
                pi.integrated.event.rag.model.EventRetrievedPassage.builder()
                        .documentId("evt-001")
                        .title("Test Event")
                        .category("WORKSHOP")
                        .passageText("A great workshop for beginners")
                        .score(0.85)
                        .build()
        );

        String result = EventRagPromptTemplates.formatRetrievedContext(passages);
        assertTrue(result.contains("evt-001"));
        assertTrue(result.contains("Test Event"));
        assertTrue(result.contains("great workshop"));
    }

    @Test
    @DisplayName("userPromptForRecommendation should include query and context")
    void testUserPromptConstruction() {
        var passages = List.of(
                pi.integrated.event.rag.model.EventRetrievedPassage.builder()
                        .documentId("evt-001")
                        .title("Business Workshop")
                        .passageText("Learn business skills")
                        .score(0.9)
                        .build()
        );

        String prompt = EventRagPromptTemplates.userPromptForRecommendation(
                "I want business English", "BUSINESS_ENGLISH", passages);

        assertTrue(prompt.contains("I want business English"));
        assertTrue(prompt.contains("BUSINESS_ENGLISH"));
        assertTrue(prompt.contains("Business Workshop"));
    }

    // ============================
    // Integration Test (without Gemini API)
    // ============================

    @Test
    @DisplayName("Full RAG pipeline should index and retrieve relevant documents")
    void testFullPipelineWithoutLlm() {
        // 1. Create documents
        EventKnowledgeDocument doc1 = EventKnowledgeDocument.builder()
                .id("evt-test-01").title("IELTS Preparation Workshop")
                .category("EXAM_PREPARATION")
                .description("Prepare for IELTS exam with mock tests")
                .skills(List.of("reading", "writing", "listening", "speaking"))
                .keywords(List.of("ielts", "exam", "preparation", "mock test"))
                .build();

        EventKnowledgeDocument doc2 = EventKnowledgeDocument.builder()
                .id("evt-test-02").title("Business Negotiation Skills")
                .category("BUSINESS_ENGLISH")
                .description("Learn negotiation techniques in English")
                .skills(List.of("negotiation", "persuasion", "business vocabulary"))
                .keywords(List.of("business", "negotiation", "deal", "meeting"))
                .build();

        // 2. Build chunks
        List<EventIndexedChunk> chunks = chunkBuilder.toChunks(List.of(doc1, doc2));
        assertEquals(2, chunks.size());

        // 3. Embed chunks
        chunks.forEach(c -> c.setEmbedding(embeddingService.embed(c.getSearchableText())));

        // 4. Index
        vectorIndex.replaceAll(chunks);
        assertTrue(vectorIndex.isReady());

        // 5. Query — Should find IELTS as more relevant
        float[] queryVec = embeddingService.embed("I want to prepare for IELTS exam");
        double simIelts = LocalHashEmbeddingService.cosineSimilarity(queryVec, chunks.get(0).getEmbedding());
        double simBusiness = LocalHashEmbeddingService.cosineSimilarity(queryVec, chunks.get(1).getEmbedding());

        assertTrue(simIelts > simBusiness,
                "IELTS document should be more relevant for IELTS query (got " + simIelts + " vs " + simBusiness + ")");
    }
}
