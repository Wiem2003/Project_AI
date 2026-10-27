package pi.integrated.preevaluation.rag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeDocument {

    private String id;
    private List<String> levels = new ArrayList<>();
    private String category;
    private String ruleTitle;
    private String rule;
    private List<String> correctExamples = new ArrayList<>();
    private List<String> incorrectExamples = new ArrayList<>();
    private String explanation;
    private List<String> keywords = new ArrayList<>();
    private String exerciseHint;
    private List<KnowledgeSource> sources = new ArrayList<>();
}
