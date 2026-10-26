package pi.integrated.course.rag;

/** Published when a course, module or lesson changes, so the RAG index of the course is refreshed. */
public record CourseContentChangedEvent(Long courseId) {
}
