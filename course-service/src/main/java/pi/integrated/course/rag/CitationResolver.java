package pi.integrated.course.rag;

import pi.integrated.course.dto.CourseAskResponse.Citation;
import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.rag.CourseRetrievalService.RetrievedChunk;
import pi.integrated.course.rag.CourseVectorIndex.IndexedCourseChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces the [S1]-style tags written by the model with human labels ([Leçon 3 – 04:12], [PDF p. 5])
 * built from chunk metadata, and lists the cited sources. Unknown tags are dropped.
 */
public final class CitationResolver {

    /** [S1], [S1, S2], [S1][S2] (the latter matched tag by tag). */
    private static final Pattern TAG_GROUP = Pattern.compile("\\[\\s*(S\\d+(?:\\s*[,;]\\s*S\\d+)*)\\s*]");
    private static final Pattern TAG = Pattern.compile("S(\\d+)");

    private CitationResolver() {
    }

    public record Resolved(String answer, List<Citation> citations) {
    }

    public static Resolved resolve(String rawAnswer, List<RetrievedChunk> passages, Long courseId) {
        Map<String, Citation> cited = new LinkedHashMap<>();
        Matcher group = TAG_GROUP.matcher(rawAnswer == null ? "" : rawAnswer);
        StringBuilder out = new StringBuilder();
        while (group.find()) {
            List<String> labels = new ArrayList<>();
            Matcher tag = TAG.matcher(group.group(1));
            while (tag.find()) {
                int index = Integer.parseInt(tag.group(1)) - 1;
                if (index < 0 || index >= passages.size()) {
                    continue;
                }
                IndexedCourseChunk chunk = passages.get(index).chunk();
                String label = label(chunk);
                if (!labels.contains(label)) {
                    labels.add(label);
                }
                cited.putIfAbsent(label, citation(chunk, label, courseId));
            }
            String replacement = labels.stream().map(l -> "[" + l + "]").reduce((a, b) -> a + " " + b).orElse("");
            group.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        group.appendTail(out);
        String answer = out.toString()
                .replace("][", "] [")
                .replaceAll("[ \\t]+([.,;:!?])", "$1")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
        return new Resolved(answer, List.copyOf(cited.values()));
    }

    public static String label(IndexedCourseChunk c) {
        return switch (c.sourceType()) {
            case TRANSCRIPT -> "Leçon " + c.lessonOrder() + " – "
                    + TimestampFormat.format(c.startTime() == null ? 0 : c.startTime());
            case LESSON_DESC -> "Leçon " + c.lessonOrder();
            case COURSE_DESC -> "Description du cours";
            case PDF -> "PDF p. " + c.page();
        };
    }

    static Citation citation(IndexedCourseChunk c, String label, Long courseId) {
        Citation.CitationBuilder b = Citation.builder()
                .label(label)
                .lessonId(c.lessonId())
                .lessonTitle(c.lessonTitle())
                .lessonOrder(c.lessonOrder())
                .sourceType(c.sourceType());
        if (c.sourceType() == RagSourceType.TRANSCRIPT && c.startTime() != null) {
            long seconds = TimestampFormat.toSeconds(c.startTime());
            b.timestamp(TimestampFormat.format(c.startTime()))
                    .startSeconds(seconds)
                    .url("/courses/" + courseId + "/lessons/" + c.lessonId() + "?t=" + seconds);
        } else if (c.sourceType() == RagSourceType.PDF) {
            b.page(c.page())
                    .documentId(c.documentId())
                    .url("/api/courses/" + courseId + "/documents/" + c.documentId() + "/file#page=" + c.page());
        } else if (c.lessonId() != null) {
            b.url("/courses/" + courseId + "/lessons/" + c.lessonId());
        } else {
            b.url("/courses/" + courseId);
        }
        return b.build();
    }
}
