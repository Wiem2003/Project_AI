package pi.integrated.course.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Validates and cleans the timestamps Gemini generates for a transcription window.
 * Gemini writes timestamps as text, so they are checked rather than trusted:
 * they must fall inside the requested window, be ordered and not overlap.
 */
public final class TranscriptNormalizer {

    /** Tolerance in seconds around window bounds. */
    static final double TOLERANCE = 3.0;

    /** Above this share of out-of-range segments, the whole window is rejected. */
    static final double MAX_INVALID_RATIO = 0.2;

    /** With at least this many segments, at least half of them must have distinct start seconds. */
    static final int MIN_SEGMENTS_FOR_SPREAD_CHECK = 5;
    static final double MIN_DISTINCT_START_RATIO = 0.5;

    private TranscriptNormalizer() {
    }

    public static class InvalidTranscriptException extends RuntimeException {
        public InvalidTranscriptException(String message) {
            super(message);
        }
    }

    /**
     * @param windowStart  start of the transcribed clip, seconds (0 for the full video)
     * @param windowEnd    end of the clip, or null when unknown (full video, unknown duration)
     */
    public static List<TranscriptSegment> normalizeWindow(List<TranscriptSegment> raw, double windowStart,
                                                          Double windowEnd) {
        List<TranscriptSegment> segments = new ArrayList<>();
        for (TranscriptSegment s : raw == null ? List.<TranscriptSegment>of() : raw) {
            if (s != null && s.text() != null && !s.text().isBlank()
                    && Double.isFinite(s.start()) && Double.isFinite(s.end())) {
                segments.add(new TranscriptSegment(s.start(), s.end(), s.text().trim()));
            }
        }
        if (segments.isEmpty()) {
            return segments;
        }

        // Clip timestamps may come back relative to the clip start instead of the full video.
        if (windowStart > 0 && windowEnd != null) {
            double clipLength = windowEnd - windowStart;
            double minStart = segments.stream().mapToDouble(TranscriptSegment::start).min().orElse(0);
            double maxStart = segments.stream().mapToDouble(TranscriptSegment::start).max().orElse(0);
            if (minStart < windowStart - TOLERANCE && maxStart <= clipLength + TOLERANCE) {
                segments = segments.stream()
                        .map(s -> new TranscriptSegment(s.start() + windowStart, s.end() + windowStart, s.text()))
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            }
        }

        // Degenerate output seen with Gemini: most segments stamped with the same time (e.g. all "00:01").
        if (segments.size() >= MIN_SEGMENTS_FOR_SPREAD_CHECK) {
            long distinctStarts = segments.stream().mapToLong(s -> (long) Math.floor(s.start())).distinct().count();
            if (distinctStarts < segments.size() * MIN_DISTINCT_START_RATIO) {
                throw new InvalidTranscriptException(String.format(
                        "only %d distinct start times for %d segments", distinctStarts, segments.size()));
            }
        }

        double lower = Math.max(0, windowStart - TOLERANCE);
        double upper = windowEnd == null ? Double.MAX_VALUE : windowEnd + TOLERANCE;
        List<TranscriptSegment> valid = new ArrayList<>();
        for (TranscriptSegment s : segments) {
            if (s.start() >= lower && s.start() <= upper) {
                valid.add(s);
            }
        }
        int invalid = segments.size() - valid.size();
        if (invalid > segments.size() * MAX_INVALID_RATIO) {
            throw new InvalidTranscriptException(String.format(
                    "%d/%d segments outside [%.0fs, %s]", invalid, segments.size(), windowStart,
                    windowEnd == null ? "end" : String.format("%.0fs", windowEnd)));
        }

        valid.sort(Comparator.comparingDouble(TranscriptSegment::start));
        List<TranscriptSegment> fixed = new ArrayList<>(valid.size());
        for (int i = 0; i < valid.size(); i++) {
            TranscriptSegment s = valid.get(i);
            Double next = i + 1 < valid.size() ? valid.get(i + 1).start() : null;
            double end = s.end();
            if (end <= s.start()) {
                end = next != null ? next : s.start() + 5.0;
            }
            if (next != null && end > next) {
                end = next;
            }
            if (windowEnd != null && end > windowEnd + TOLERANCE) {
                end = Math.max(s.start(), windowEnd);
            }
            fixed.add(new TranscriptSegment(s.start(), end, s.text()));
        }
        return fixed;
    }

    /** Concatenates normalized windows, dropping segments repeated across a window boundary. */
    public static List<TranscriptSegment> merge(List<List<TranscriptSegment>> windows) {
        List<TranscriptSegment> out = new ArrayList<>();
        double lastEnd = -1;
        for (List<TranscriptSegment> window : windows) {
            for (TranscriptSegment s : window) {
                if (s.start() < lastEnd - 1.0) {
                    continue;
                }
                out.add(s);
                lastEnd = Math.max(lastEnd, s.end());
            }
        }
        return out;
    }
}
