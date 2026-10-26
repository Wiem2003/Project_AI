package pi.integrated.event.rag.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRetrievedPassage {

    private String documentId;
    private String title;
    private String category;
    private String passageText;
    private double score;
}
