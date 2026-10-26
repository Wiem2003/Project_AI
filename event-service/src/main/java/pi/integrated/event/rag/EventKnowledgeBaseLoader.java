package pi.integrated.event.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import pi.integrated.event.rag.model.EventKnowledgeDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Charge les documents de la base de connaissances depuis les fichiers JSON
 * situés dans classpath:rag/knowledge/*.json
 */
@Component
@RequiredArgsConstructor
public class EventKnowledgeBaseLoader {

    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper objectMapper;

    /**
     * Charge tous les documents de connaissances depuis les fichiers JSON.
     */
    public List<EventKnowledgeDocument> loadAll() throws Exception {
        Resource[] resources = resourcePatternResolver.getResources("classpath:rag/knowledge/*.json");
        List<EventKnowledgeDocument> all = new ArrayList<>();
        for (Resource resource : resources) {
            JsonNode root = objectMapper.readTree(resource.getInputStream());
            JsonNode docs = root.get("documents");
            if (docs == null || !docs.isArray()) {
                continue;
            }
            for (JsonNode node : docs) {
                all.add(objectMapper.treeToValue(node, EventKnowledgeDocument.class));
            }
        }
        return all;
    }
}
