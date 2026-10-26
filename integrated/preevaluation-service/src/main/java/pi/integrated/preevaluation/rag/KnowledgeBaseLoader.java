package pi.integrated.preevaluation.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import pi.integrated.preevaluation.rag.model.KnowledgeDocument;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KnowledgeBaseLoader {

    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper objectMapper;

    public List<KnowledgeDocument> loadAll() throws Exception {
        Resource[] resources = resourcePatternResolver.getResources("classpath:rag/knowledge/*.json");
        List<KnowledgeDocument> all = new ArrayList<>();
        for (Resource resource : resources) {
            JsonNode root = objectMapper.readTree(resource.getInputStream());
            JsonNode docs = root.get("documents");
            if (docs == null || !docs.isArray()) {
                continue;
            }
            for (JsonNode node : docs) {
                all.add(objectMapper.treeToValue(node, KnowledgeDocument.class));
            }
        }
        return all;
    }
}
