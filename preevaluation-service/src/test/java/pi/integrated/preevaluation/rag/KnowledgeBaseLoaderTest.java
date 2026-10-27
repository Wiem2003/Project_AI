package pi.integrated.preevaluation.rag;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import pi.integrated.preevaluation.rag.model.KnowledgeDocument;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseLoaderTest {

    @Test
    void loadsPedagogicalDocumentsFromClasspath() throws Exception {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(resolver, new ObjectMapper());

        List<KnowledgeDocument> docs = loader.loadAll();
        assertFalse(docs.isEmpty(), "expected CEFR knowledge JSON to load");
        assertTrue(docs.stream().anyMatch(d -> "a2-grammar-present-continuous".equals(d.getId())));
        assertTrue(docs.stream().allMatch(d -> d.getSources() != null && !d.getSources().isEmpty()));
    }
}
