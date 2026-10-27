package pi.integrated.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseAskRequest {
    private String question;
    /** Lesson being watched (optional): its passages get a small ranking bonus. */
    private Long lessonId;
}
