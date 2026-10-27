package pi.integrated.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pi.integrated.course.entity.RagSourceType;

import java.util.List;

/** Admin debug view of a retrieval: which passages were found and with what scores. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSearchResponse {

    private String question;
    private double minScore;
    private double bestSimilarity;
    /** False when no passage reached minScore: the assistant would answer "not in the course". */
    private boolean answerable;
    private List<Passage> passages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Passage {
        private Long chunkId;
        private Long lessonId;
        private Integer lessonOrder;
        private String lessonTitle;
        private RagSourceType sourceType;
        private Double startTime;
        private Integer page;
        private double similarity;
        private double score;
        private String text;
    }
}
