package pi.integrated.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pi.integrated.course.dto.CourseAskResponse.Citation;

import java.util.List;

/** Quiz generated from the indexed passages of one lesson; every question keeps its source passage. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonQuizResponse {

    private Long courseId;
    private Long lessonId;
    private Integer lessonOrder;
    private String lessonTitle;
    private String level;
    private String title;
    private String description;
    private Integer suggestedPassingScore;
    private Integer suggestedTimeLimit;
    private List<Question> questions;
    /** Number of questions asked by the admin. */
    private Integer requested;
    /** Number of questions returned by the model (requested + 3, to make up for rejected ones). */
    private Integer generated;
    /** How the passages were chosen: "topic", "mmr" or "all". */
    private String selectionMode;
    /** Questions dropped by the server-side checks. */
    private List<Rejection> rejected;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Question {
        private String text;
        /** MULTIPLE_CHOICE or TRUE_FALSE (same values as quiz-feedback-service). */
        private String type;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private Integer points;
        /** Where the answer is in the course: label, lesson, timestamp and link. */
        private Citation source;
        /** The course passage the question was written from, shown to the student after a wrong answer. */
        private String sourceExcerpt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rejection {
        private String reason;
        /** Beginning of the rejected question. */
        private String question;
    }
}
