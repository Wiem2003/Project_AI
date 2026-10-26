package pi.integrated.preevaluation.rag;

import org.junit.jupiter.api.Test;
import pi.integrated.preevaluation.rag.model.IndexedChunk;
import pi.integrated.preevaluation.rag.model.KnowledgeDocument;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PedagogicalChunkBuilderTest {

    @Test
    void buildsSearchableChunkFromDocument() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId("a2-grammar-present-continuous");
        doc.setLevels(List.of("A2", "B1"));
        doc.setCategory("GRAMMAR");
        doc.setRuleTitle("Present continuous");
        doc.setRule("Use am/is/are + -ing");
        doc.setExplanation("For actions happening now.");
        doc.setKeywords(List.of("now", "reading"));

        List<IndexedChunk> chunks = new PedagogicalChunkBuilder().toChunks(doc);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getSearchableText().contains("Present continuous"));
        assertTrue(chunks.get(0).getPassageText().contains("Explanation"));
    }
}
