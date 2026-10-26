package pi.integrated.preevaluation.rag.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class IndexedChunk {
    String chunkId;
    String documentId;
    String ruleTitle;
    String levelScope;
    String category;
    String searchableText;
    String passageText;
    List<KnowledgeSource> sources;
    float[] embedding;
}
