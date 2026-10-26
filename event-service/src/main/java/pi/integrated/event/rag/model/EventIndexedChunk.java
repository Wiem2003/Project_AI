package pi.integrated.event.rag.model;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventIndexedChunk {

    private String chunkId;
    private String documentId;
    private String title;
    private String category;
    private String searchableText;
    private String passageText;
    private List<String> skills;
    private float[] embedding;
}
