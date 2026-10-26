package pi.integrated.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pi.integrated.course.entity.RagSourceType;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAskResponse {

    /** Answer with inline citations such as [Leçon 3 – 04:12] or [PDF p. 5]. */
    private String answer;

    /** False when the course does not contain the information (no LLM call or LLM said insufficient). */
    private boolean answered;

    private List<Citation> citations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        /** Label as it appears in the answer, without brackets, e.g. "Leçon 3 – 04:12". */
        private String label;
        private Long lessonId;
        private String lessonTitle;
        private Integer lessonOrder;
        private RagSourceType sourceType;
        /** mm:ss or hh:mm:ss (transcript only). */
        private String timestamp;
        /** Seconds from the start of the video (transcript only), for the player. */
        private Long startSeconds;
        /** PDF page (pdf only). */
        private Integer page;
        private Long documentId;
        /** Front-end link: lesson page with ?t=seconds for videos, PDF file with #page= for documents. */
        private String url;
    }
}
