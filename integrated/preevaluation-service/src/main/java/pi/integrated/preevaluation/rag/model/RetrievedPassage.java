package pi.integrated.preevaluation.rag.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RetrievedPassage {
    String documentId;
    String ruleTitle;
    String passageText;
    double score;
    List<KnowledgeSource> sources;
}
