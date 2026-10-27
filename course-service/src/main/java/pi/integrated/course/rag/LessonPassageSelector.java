package pi.integrated.course.rag;

import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.rag.CourseVectorIndex.IndexedCourseChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses the passages of ONE lesson a quiz is written from, using the embeddings already indexed (no network call).
 * <ul>
 *   <li>{@code topic}: passages whose similarity to the topic reaches the threshold, best first, at most k;</li>
 *   <li>{@code mmr}: no topic and more than k passages: Maximal Marginal Relevance around the lesson centroid,
 *       so the quiz covers the whole lesson without near-duplicate passages;</li>
 *   <li>{@code all}: no topic and at most k passages.</li>
 * </ul>
 * The selected passages are always returned in lesson order (description, then transcript by timestamp).
 */
public final class LessonPassageSelector {

    public static final String MODE_TOPIC = "topic";
    public static final String MODE_MMR = "mmr";
    public static final String MODE_ALL = "all";

    public record Selection(List<IndexedCourseChunk> passages, String mode) {
    }

    private LessonPassageSelector() {
    }

    /** Description and transcript passages of the lesson, in lesson order. */
    public static List<IndexedCourseChunk> lessonPassages(List<IndexedCourseChunk> all, Long lessonId) {
        return all.stream()
                .filter(c -> lessonId.equals(c.lessonId()))
                .filter(c -> c.sourceType() == RagSourceType.LESSON_DESC || c.sourceType() == RagSourceType.TRANSCRIPT)
                .sorted(Comparator.comparing((IndexedCourseChunk c) -> c.sourceType() == RagSourceType.TRANSCRIPT)
                        .thenComparing(c -> c.startTime() == null ? 0.0 : c.startTime())
                        .thenComparing(c -> c.chunkId() == null ? 0L : c.chunkId()))
                .toList();
    }

    /**
     * @param lessonPassages passages in lesson order (see {@link #lessonPassages})
     * @param topicVector    embedding of the topic, or null when no topic was given
     */
    public static Selection select(List<IndexedCourseChunk> lessonPassages, float[] topicVector,
                                   double minScore, int k, double lambda) {
        if (topicVector != null) {
            return new Selection(byTopic(lessonPassages, topicVector, minScore, k), MODE_TOPIC);
        }
        if (lessonPassages.size() <= k) {
            return new Selection(List.copyOf(lessonPassages), MODE_ALL);
        }
        return new Selection(mmr(lessonPassages, k, lambda), MODE_MMR);
    }

    static List<IndexedCourseChunk> byTopic(List<IndexedCourseChunk> passages, float[] topic, double minScore, int k) {
        List<Integer> kept = new ArrayList<>();
        double[] sims = new double[passages.size()];
        for (int i = 0; i < passages.size(); i++) {
            sims[i] = CourseRetrievalService.cosine(topic, passages.get(i).vector());
            if (sims[i] >= minScore) {
                kept.add(i);
            }
        }
        kept.sort(Comparator.comparingDouble((Integer i) -> sims[i]).reversed().thenComparing(i -> i));
        return inLessonOrder(passages, kept.subList(0, Math.min(k, kept.size())));
    }

    /**
     * Greedy MMR: at each step pick the passage maximising
     * {@code lambda * sim(centroid, p) - (1 - lambda) * max sim(p, already selected)}.
     * Ties go to the earliest passage, so the result is deterministic.
     */
    static List<IndexedCourseChunk> mmr(List<IndexedCourseChunk> passages, int k, double lambda) {
        float[] centroid = centroid(passages);
        int n = passages.size();
        double[] relevance = new double[n];
        double[] maxSimToSelected = new double[n];
        boolean[] chosen = new boolean[n];
        for (int i = 0; i < n; i++) {
            relevance[i] = CourseRetrievalService.cosine(centroid, passages.get(i).vector());
            maxSimToSelected[i] = Double.NEGATIVE_INFINITY;
        }
        List<Integer> selected = new ArrayList<>();
        while (selected.size() < Math.min(k, n)) {
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (chosen[i]) {
                    continue;
                }
                double redundancy = selected.isEmpty() ? 0.0 : maxSimToSelected[i];
                double score = lambda * relevance[i] - (1 - lambda) * redundancy;
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            chosen[best] = true;
            selected.add(best);
            for (int i = 0; i < n; i++) {
                if (!chosen[i]) {
                    double sim = CourseRetrievalService.cosine(passages.get(i).vector(), passages.get(best).vector());
                    maxSimToSelected[i] = Math.max(maxSimToSelected[i], sim);
                }
            }
        }
        return inLessonOrder(passages, selected);
    }

    /** Mean of the passage vectors, L2-normalised (vectors of another dimension are ignored). */
    static float[] centroid(List<IndexedCourseChunk> passages) {
        float[] first = passages.stream().map(IndexedCourseChunk::vector)
                .filter(v -> v != null && v.length > 0).findFirst().orElse(null);
        if (first == null) {
            return new float[0];
        }
        double[] sum = new double[first.length];
        for (IndexedCourseChunk p : passages) {
            float[] v = p.vector();
            if (v != null && v.length == sum.length) {
                for (int i = 0; i < v.length; i++) {
                    sum[i] += v[i];
                }
            }
        }
        double norm = 0.0;
        for (double x : sum) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        float[] out = new float[sum.length];
        for (int i = 0; i < sum.length; i++) {
            out[i] = norm == 0.0 ? 0f : (float) (sum[i] / norm);
        }
        return out;
    }

    private static List<IndexedCourseChunk> inLessonOrder(List<IndexedCourseChunk> passages, List<Integer> indexes) {
        return indexes.stream().sorted().map(passages::get).toList();
    }
}
