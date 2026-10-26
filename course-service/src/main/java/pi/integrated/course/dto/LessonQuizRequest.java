package pi.integrated.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonQuizRequest {
    private Long lessonId;
    /** 1 to 10 (default 5). */
    private Integer questionCount;
    /** CEFR level A1..C2 used to adapt the wording (optional; defaults to the course level when it is a CEFR code). */
    private String level;
    /** MULTIPLE_CHOICE (default), TRUE_FALSE or MIXED. */
    private String questionType;
    /**
     * Optional topic: only the lesson passages close to it (embeddings, same threshold as "Ask the course")
     * are used. If fewer than 2 passages match, the request is refused with 422.
     */
    private String topic;
}
