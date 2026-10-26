package pi.integrated.preevaluation.rag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeSource {
    private String label;
    private String type;
}
