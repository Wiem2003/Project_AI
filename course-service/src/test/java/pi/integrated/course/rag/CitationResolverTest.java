package pi.integrated.course.rag;

import org.junit.jupiter.api.Test;
import pi.integrated.course.dto.CourseAskResponse.Citation;
import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.rag.CourseRetrievalService.RetrievedChunk;
import pi.integrated.course.rag.CourseVectorIndex.IndexedCourseChunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitationResolverTest {

    private static RetrievedChunk transcript(long lessonId, int order, double start) {
        return new RetrievedChunk(new IndexedCourseChunk(1L, 9L, lessonId, "Present perfect", order,
                RagSourceType.TRANSCRIPT, start, start + 20, null, null, "text", new float[0]), 0.8, 0.8);
    }

    private static RetrievedChunk pdf(int page) {
        return new RetrievedChunk(new IndexedCourseChunk(2L, 9L, null, null, null,
                RagSourceType.PDF, null, null, page, 7L, "text", new float[0]), 0.7, 0.7);
    }

    private static RetrievedChunk lessonDesc(long lessonId, int order) {
        return new RetrievedChunk(new IndexedCourseChunk(3L, 9L, lessonId, "Simple past", order,
                RagSourceType.LESSON_DESC, null, null, null, null, "text", new float[0]), 0.6, 0.6);
    }

    @Test
    void replacesTagsWithLessonTimestampAndPdfPageLabels() {
        var passages = List.of(transcript(12, 3, 252.4), pdf(5));

        var resolved = CitationResolver.resolve("Use have + past participle [S1]. Written is irregular [S2].",
                passages, 9L);

        assertEquals("Use have + past participle [Leçon 3 – 04:12]. Written is irregular [PDF p. 5].",
                resolved.answer());
        assertEquals(2, resolved.citations().size());

        Citation video = resolved.citations().get(0);
        assertEquals("Leçon 3 – 04:12", video.getLabel());
        assertEquals(RagSourceType.TRANSCRIPT, video.getSourceType());
        assertEquals("04:12", video.getTimestamp());
        assertEquals(252L, video.getStartSeconds());
        assertEquals("/courses/9/lessons/12?t=252", video.getUrl());

        Citation pdf = resolved.citations().get(1);
        assertEquals(5, pdf.getPage());
        assertEquals("/api/courses/9/documents/7/file#page=5", pdf.getUrl());
    }

    @Test
    void handlesGroupedTagsAndDropsUnknownOnes() {
        var passages = List.of(transcript(12, 3, 252.4), pdf(5));

        var resolved = CitationResolver.resolve("A [S1, S2]. B [S9]. C [S2][S1].", passages, 9L);

        assertEquals("A [Leçon 3 – 04:12] [PDF p. 5]. B. C [PDF p. 5] [Leçon 3 – 04:12].", resolved.answer());
        assertEquals(2, resolved.citations().size(), "each source listed once");
    }

    @Test
    void answerWithoutValidTagHasNoCitation() {
        var resolved = CitationResolver.resolve("No tags here [S4].", List.of(pdf(1)), 9L);

        assertTrue(resolved.citations().isEmpty());
        assertEquals("No tags here.", resolved.answer());
    }

    @Test
    void labelsDescriptions() {
        assertEquals("Leçon 4", CitationResolver.label(lessonDesc(20, 4).chunk()));
        var courseDesc = new IndexedCourseChunk(4L, 9L, null, null, null, RagSourceType.COURSE_DESC,
                null, null, null, null, "t", new float[0]);
        assertEquals("Description du cours", CitationResolver.label(courseDesc));
        assertEquals("/courses/9/lessons/20",
                CitationResolver.resolve("x [S1]", List.of(lessonDesc(20, 4)), 9L).citations().get(0).getUrl());
    }

    @Test
    void hourLongVideosUseHhMmSs() {
        assertEquals("Leçon 1 – 01:02:05", CitationResolver.label(transcript(1, 1, 3725).chunk()));
    }
}
