package pi.integrated.course.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pi.integrated.course.ai.GeminiClient;
import pi.integrated.course.config.CourseRagProperties;
import pi.integrated.course.rag.CourseVectorIndex.IndexedCourseChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the passages of ONE course that answer a question.
 * Only chunks of {@code courseId} are ever considered; chunks of the lesson being watched get a small
 * ranking bonus. The similarity threshold is applied to the raw similarity, so the bonus can reorder
 * relevant passages but never lets an irrelevant one through.
 */
@Service
@RequiredArgsConstructor
public class CourseRetrievalService {

    private final CourseVectorIndex vectorIndex;
    private final GeminiClient geminiClient;
    private final CourseRagProperties ragProperties;

    public record RetrievedChunk(IndexedCourseChunk chunk, double similarity, double score) {
    }

    /**
     * @param indexEmpty true when the course has no indexed content at all
     * @param bestSimilarity best raw similarity among all chunks (useful to calibrate min-score)
     */
    public record RetrievalResult(List<RetrievedChunk> passages, boolean indexEmpty, double bestSimilarity) {
        public boolean hasPassages() {
            return !passages.isEmpty();
        }
    }

    public RetrievalResult retrieve(Long courseId, String question, Long currentLessonId) {
        List<IndexedCourseChunk> chunks = vectorIndex.chunksOf(courseId);
        if (chunks.isEmpty()) {
            return new RetrievalResult(List.of(), true, 0.0);
        }
        float[] query = geminiClient.embedQuery(question);
        return rank(chunks, query, currentLessonId, ragProperties.getTopK(),
                ragProperties.getMinScore(), ragProperties.getCurrentLessonBoost());
    }

    static RetrievalResult rank(List<IndexedCourseChunk> chunks, float[] query, Long currentLessonId,
                                int topK, double minScore, double lessonBoost) {
        List<RetrievedChunk> candidates = new ArrayList<>();
        double best = 0.0;
        for (IndexedCourseChunk c : chunks) {
            double sim = cosine(query, c.vector());
            best = Math.max(best, sim);
            if (sim < minScore) {
                continue;
            }
            boolean currentLesson = currentLessonId != null && currentLessonId.equals(c.lessonId());
            candidates.add(new RetrievedChunk(c, sim, sim + (currentLesson ? lessonBoost : 0.0)));
        }
        candidates.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        List<RetrievedChunk> top = candidates.subList(0, Math.min(Math.max(1, topK), candidates.size()));
        return new RetrievalResult(List.copyOf(top), false, best);
    }

    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
