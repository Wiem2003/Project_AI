package pi.integrated.preevaluation.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pi.integrated.preevaluation.config.PreevaluationAiProperties;
import pi.integrated.preevaluation.config.PreevaluationRagProperties;
import pi.integrated.preevaluation.rag.model.IndexedChunk;
import pi.integrated.preevaluation.rag.model.RetrievedPassage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagRetrievalServiceTest {

    private RagVectorIndex index;
    private RagRetrievalService retrievalService;
    private LocalHashEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new LocalHashEmbeddingService();
        index = new RagVectorIndex();
        PreevaluationRagProperties props = new PreevaluationRagProperties();
        props.setMinScore(0.05);
        props.setTopK(3);

        GroqEmbeddingClient groqEmbeddingClient = new GroqEmbeddingClient(
                new PreevaluationAiProperties(),
                null,
                new ObjectMapper(),
                embeddingService
        ) {
            @Override
            public List<float[]> embedTexts(String apiKey, List<String> texts) {
                return texts.stream().map(embeddingService::embed).toList();
            }
        };

        retrievalService = new RagRetrievalService(props, index, groqEmbeddingClient, embeddingService);

        index.replaceAll(List.of(
                chunk("a2-grammar-present-continuous", "A2", "GRAMMAR",
                        "present continuous am is are ing now right now I am reading"),
                chunk("a2-grammar-present-simple-third-person", "A2", "GRAMMAR",
                        "present simple third person goes every day she goes"),
                chunk("b2-grammar-passive-voice", "B2", "GRAMMAR",
                        "passive voice was written past participle")
        ));
    }

    @Test
    void retrievesPresentContinuousForReadingExampleQuestion() {
        RagQueryContext ctx = RagQueryContext.builder()
                .questionText("I ___ a book right now.")
                .studentAnswer("read")
                .correctAnswer("am reading")
                .category("GRAMMAR")
                .studentLevel("A2")
                .build();

        List<RetrievedPassage> hits = retrievalService.retrieve(null, ctx);
        assertFalse(hits.isEmpty());
        assertEquals("a2-grammar-present-continuous", hits.get(0).getDocumentId());
    }

    @Test
    void filtersByStudentLevel() {
        RagQueryContext ctx = RagQueryContext.builder()
                .questionText("The report ___ last week.")
                .studentAnswer("write")
                .correctAnswer("was written")
                .category("GRAMMAR")
                .studentLevel("A2")
                .build();

        List<RetrievedPassage> hits = retrievalService.retrieve(null, ctx);
        assertTrue(hits.stream().noneMatch(h -> h.getDocumentId().equals("b2-grammar-passive-voice")));
    }

    @Test
    void returnsEmptyWhenIndexEmpty() {
        index.clear();
        List<RetrievedPassage> hits = retrievalService.retrieve(null, RagQueryContext.builder()
                .questionText("test")
                .studentLevel("A2")
                .build());
        assertTrue(hits.isEmpty());
    }

    private IndexedChunk chunk(String id, String level, String category, String searchable) {
        float[] emb = embeddingService.embed(searchable);
        return IndexedChunk.builder()
                .chunkId(id + "#0")
                .documentId(id)
                .ruleTitle(id)
                .levelScope(level)
                .category(category)
                .searchableText(searchable)
                .passageText("Rule text for " + id)
                .sources(List.of())
                .embedding(emb)
                .build();
    }
}
