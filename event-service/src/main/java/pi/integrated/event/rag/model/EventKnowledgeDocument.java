package pi.integrated.event.rag.model;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventKnowledgeDocument {

    private String id;
    private String category;
    private String title;
    private String description;
    private String targetAudience;
    private List<String> skills;
    private String duration;
    private String format;
    private String typicalFeedback;
    private List<String> keywords;
}
