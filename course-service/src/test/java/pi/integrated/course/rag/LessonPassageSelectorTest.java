package pi.integrated.course.rag;

import org.junit.jupiter.api.Test;
import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.rag.CourseVectorIndex.IndexedCourseChunk;
import pi.integrated.course.rag.LessonPassageSelector.Selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LessonPassageSelectorTest {

    private static IndexedCourseChunk transcript(long id, long lessonId, double start, float... vector) {
        return new IndexedCourseChunk(id, 1L, lessonId, "Daily routine", 1, RagSourceType.TRANSCRIPT,
                start, start + 30, null, null, "t" + id, vector);
    }

    private static IndexedCourseChunk description(long id, long lessonId, float... vector) {
        return new IndexedCourseChunk(id, 1L, lessonId, "Daily routine", 1, RagSourceType.LESSON_DESC,
                null, null, null, null, "desc", vector);
    }

    private static void assertLessonOrder(List<IndexedCourseChunk> passages) {
        for (int i = 1; i < passages.size(); i++) {
            IndexedCourseChunk prev = passages.get(i - 1);
            IndexedCourseChunk cur = passages.get(i);
            assertNotEquals(RagSourceType.LESSON_DESC, cur.sourceType(), "description first");
            if (prev.sourceType() == RagSourceType.TRANSCRIPT) {
                assertTrue(cur.startTime() >= prev.startTime(), "video order");
            }
        }
    }

    @Test
    void keepsOnlyTheLessonDescriptionAndTranscriptInLessonOrder() {
        List<IndexedCourseChunk> all = List.of(
                transcript(3, 10, 200), transcript(2, 10, 50), description(1, 10),
                transcript(9, 11, 0),
                new IndexedCourseChunk(5L, 1L, null, null, null, RagSourceType.PDF, null, null, 1, 7L, "pdf", null));

        List<IndexedCourseChunk> passages = LessonPassageSelector.lessonPassages(all, 10L);

        assertEquals(List.of(1L, 2L, 3L), passages.stream().map(IndexedCourseChunk::chunkId).toList());
    }

    @Test
    void topicKeepsPassagesAboveTheThresholdThenPutsThemBackInVideoOrder() {
        List<IndexedCourseChunk> lesson = List.of(
                description(1, 10, 0, 0, 1),
                transcript(2, 10, 30, 0.6f, 0.8f, 0),   // sim 0.60
                transcript(3, 10, 90, 0, 1, 0),         // sim 0.00
                transcript(4, 10, 150, 1, 0, 0),        // sim 1.00 (best, but later in the video)
                transcript(5, 10, 210, 0.5f, 0.87f, 0)); // sim 0.50 < 0.55

        Selection s = LessonPassageSelector.select(lesson, new float[]{1, 0, 0}, 0.55, 15, 0.5);

        assertEquals("topic", s.mode());
        assertEquals(List.of(2L, 4L), s.passages().stream().map(IndexedCourseChunk::chunkId).toList());
    }

    @Test
    void topicKeepsAtMostKBestPassages() {
        List<IndexedCourseChunk> lesson = List.of(
                transcript(1, 10, 0, 0.6f, 0.8f, 0),   // 0.60
                transcript(2, 10, 30, 1, 0, 0),        // 1.00
                transcript(3, 10, 60, 0.8f, 0.6f, 0)); // 0.80

        Selection s = LessonPassageSelector.select(lesson, new float[]{1, 0, 0}, 0.55, 2, 0.5);

        assertEquals(List.of(2L, 3L), s.passages().stream().map(IndexedCourseChunk::chunkId).toList());
    }

    @Test
    void topicOutsideTheLessonSelectsNothing() {
        List<IndexedCourseChunk> lesson = List.of(transcript(1, 10, 0, 0, 1, 0), transcript(2, 10, 30, 0, 0, 1));

        assertTrue(LessonPassageSelector.select(lesson, new float[]{1, 0, 0}, 0.55, 15, 0.5).passages().isEmpty());
    }

    @Test
    void shortLessonWithoutTopicUsesEveryPassage() {
        List<IndexedCourseChunk> lesson = List.of(transcript(1, 10, 0, 1, 0), transcript(2, 10, 30, 0, 1));

        Selection s = LessonPassageSelector.select(lesson, null, 0.55, 15, 0.5);

        assertEquals("all", s.mode());
        assertEquals(lesson, s.passages());
    }

    @Test
    void longLessonWithoutTopicUsesDeterministicMmrInVideoOrder() {
        Random random = new Random(42);
        List<IndexedCourseChunk> chunks = new ArrayList<>();
        chunks.add(description(1, 10, randomVector(random)));
        for (int i = 0; i < 30; i++) {
            chunks.add(transcript(100 + i, 10, i * 30, randomVector(random)));
        }
        List<IndexedCourseChunk> shuffled = new ArrayList<>(chunks);
        Collections.shuffle(shuffled, new Random(7));
        List<IndexedCourseChunk> lesson = LessonPassageSelector.lessonPassages(shuffled, 10L);

        Selection first = LessonPassageSelector.select(lesson, null, 0.55, 15, 0.5);
        Selection second = LessonPassageSelector.select(lesson, null, 0.55, 15, 0.5);

        assertEquals("mmr", first.mode());
        assertEquals(15, first.passages().size());
        assertEquals(first.passages(), second.passages(), "deterministic");
        assertEquals(15, first.passages().stream().distinct().count());
        assertLessonOrder(first.passages());
    }

    @Test
    void mmrAvoidsNearDuplicatePassages() {
        // Three copies of the same idea and two distinct ideas: with k = 3, MMR keeps one copy and both others.
        List<IndexedCourseChunk> lesson = List.of(
                transcript(1, 10, 0, 1, 0, 0),
                transcript(2, 10, 30, 1, 0.01f, 0),
                transcript(3, 10, 60, 1, 0, 0.01f),
                transcript(4, 10, 90, 0, 1, 0),
                transcript(5, 10, 120, 0, 0, 1));

        List<Long> ids = LessonPassageSelector.mmr(lesson, 3, 0.5).stream().map(IndexedCourseChunk::chunkId).toList();

        assertEquals(3, ids.size());
        assertEquals(1, ids.stream().filter(id -> id <= 3).count(), "only one of the near-duplicates");
        assertTrue(ids.containsAll(List.of(4L, 5L)));
    }

    private static float[] randomVector(Random random) {
        float[] v = new float[8];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) random.nextGaussian();
        }
        return v;
    }
}
