package pi.integrated.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pi.integrated.course.rag.IngestionRun;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagStatusResponse {

    private Long courseId;
    /** Last re-index job since the service started, null if none. */
    private IngestionRun lastRun;
    private long chunkCount;
    private Map<String, Long> chunksBySourceType;
    private int documentCount;
    private List<LessonTranscriptStatus> lessons;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LessonTranscriptStatus {
        private Long lessonId;
        private Integer lessonOrder;
        private String lessonTitle;
        private String videoUrl;
        /** NO_VIDEO, NOT_YOUTUBE, NOT_STARTED, PENDING, RUNNING, DONE, FAILED */
        private String transcriptStatus;
        private Integer segmentCount;
        private String error;
        private LocalDateTime updatedAt;
    }
}
