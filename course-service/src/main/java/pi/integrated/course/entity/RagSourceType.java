package pi.integrated.course.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RagSourceType {
    COURSE_DESC("course_desc"),
    LESSON_DESC("lesson_desc"),
    TRANSCRIPT("transcript"),
    PDF("pdf");

    private final String apiValue;

    RagSourceType(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }
}
