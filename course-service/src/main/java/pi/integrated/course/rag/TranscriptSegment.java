package pi.integrated.course.rag;

/** One timed piece of a video transcript. Times are seconds from the start of the full video. */
public record TranscriptSegment(double start, double end, String text) {
}
