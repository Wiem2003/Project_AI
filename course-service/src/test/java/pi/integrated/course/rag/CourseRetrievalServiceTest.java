package pi.integrated.course.rag;

import org.junit.jupiter.api.Test;
import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.rag.CourseRetrievalService.RetrievalResult;
import pi.integrated.course.rag.CourseVectorIndex.IndexedCourseChunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourseRetrievalServiceTest {

    private static IndexedCourseChunk chunk(long id, Long lessonId, float... vector) {
        return new IndexedCourseChunk(id, 1L, lessonId, "L", 1, RagSourceType.TRANSCRIPT, 0.0, 1.0,
                null, null, "t" + id, vector);
    }

    private static final List<IndexedCourseChunk> CHUNKS = List.of(
            chunk(1, 10L, 1f, 0f),         // similarity 1.0, other lesson
            chunk(2, 20L, 0.95f, 0.31f),   // similarity ~0.95, current lesson
            chunk(3, 20L, 0f, 1f));        // similarity 0, current lesson

    @Test
    void currentLessonBonusReordersRelevantPassages() {
        RetrievalResult r = CourseRetrievalService.rank(CHUNKS, new float[]{1f, 0f}, 20L, 5, 0.55, 0.05);

        assertEquals(List.of(2L, 1L), r.passages().stream().map(p -> p.chunk().chunkId()).toList());
        assertEquals(1.0, r.bestSimilarity(), 1e-6);
    }

    @Test
    void bonusNeverLetsAnIrrelevantPassageThrough() {
        RetrievalResult r = CourseRetrievalService.rank(CHUNKS, new float[]{1f, 0f}, 20L, 5, 0.55, 0.5);

        assertTrue(r.passages().stream().noneMatch(p -> p.chunk().chunkId() == 3L));
    }

    @Test
    void nothingAboveThresholdGivesNoPassage() {
        RetrievalResult r = CourseRetrievalService.rank(CHUNKS, new float[]{-1f, 0f}, null, 5, 0.55, 0.05);

        assertFalse(r.hasPassages());
        assertFalse(r.indexEmpty());
    }

    @Test
    void topKLimitsTheNumberOfPassages() {
        RetrievalResult r = CourseRetrievalService.rank(CHUNKS, new float[]{1f, 0.1f}, null, 1, 0.0, 0.0);

        assertEquals(1, r.passages().size());
    }

    @Test
    void cosineHandlesMismatchedOrZeroVectors() {
        assertEquals(0.0, CourseRetrievalService.cosine(new float[]{1f}, new float[]{1f, 0f}));
        assertEquals(0.0, CourseRetrievalService.cosine(new float[]{0f, 0f}, new float[]{1f, 0f}));
        assertEquals(1.0, CourseRetrievalService.cosine(new float[]{2f, 0f}, new float[]{1f, 0f}), 1e-9);
    }
}
