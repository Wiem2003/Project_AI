package pi.integrated.course.rag;

import org.junit.jupiter.api.Test;
import pi.integrated.course.rag.TranscriptNormalizer.InvalidTranscriptException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptNormalizerTest {

    @Test
    void shiftsClipRelativeTimestampsToAbsoluteOnes() {
        List<TranscriptSegment> out = TranscriptNormalizer.normalizeWindow(List.of(
                new TranscriptSegment(5, 10, "a"),
                new TranscriptSegment(12, 20, "b")), 600, 1200.0);

        assertEquals(605.0, out.get(0).start());
        assertEquals(612.0, out.get(1).start());
    }

    @Test
    void keepsAbsoluteTimestampsUnchanged() {
        List<TranscriptSegment> out = TranscriptNormalizer.normalizeWindow(List.of(
                new TranscriptSegment(605, 610, "a"),
                new TranscriptSegment(700, 705, "b")), 600, 1200.0);

        assertEquals(605.0, out.get(0).start());
        assertEquals(700.0, out.get(1).start());
    }

    @Test
    void rejectsWindowWhenTooManyTimestampsAreOutOfRange() {
        assertThrows(InvalidTranscriptException.class, () -> TranscriptNormalizer.normalizeWindow(List.of(
                new TranscriptSegment(5000, 5005, "invented"),
                new TranscriptSegment(700, 705, "ok")), 600, 1200.0));
    }

    @Test
    void rejectsDegenerateTimestampsAllEqual() {
        // Real Gemini failure: every segment stamped 00:01.
        List<TranscriptSegment> degenerate = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new TranscriptSegment(1.0, 1.0, "line " + i)).toList();

        assertThrows(InvalidTranscriptException.class,
                () -> TranscriptNormalizer.normalizeWindow(degenerate, 0, null));
    }

    @Test
    void rejectsTimestampsBeyondTheEndOfTheVideo() {
        // Real Gemini failure: timestamps drifting past the end of a 3:33 video.
        List<TranscriptSegment> drifting = List.of(
                new TranscriptSegment(18, 22, "a"), new TranscriptSegment(250, 255, "b"),
                new TranscriptSegment(270, 275, "c"), new TranscriptSegment(285, 290, "d"));

        assertThrows(InvalidTranscriptException.class,
                () -> TranscriptNormalizer.normalizeWindow(drifting, 0, 213.0));
    }

    @Test
    void sortsSegmentsAndFixesMissingOrOverlappingEnds() {
        List<TranscriptSegment> out = TranscriptNormalizer.normalizeWindow(List.of(
                new TranscriptSegment(20, 10, "end before start"),
                new TranscriptSegment(0, 30, "overlaps next"),
                new TranscriptSegment(40, 40, "zero length, last")), 0, null);

        assertEquals(List.of(0.0, 20.0, 40.0), out.stream().map(TranscriptSegment::start).toList());
        assertEquals(20.0, out.get(0).end(), "end clipped to next start");
        assertEquals(40.0, out.get(1).end(), "missing end replaced by next start");
        assertEquals(45.0, out.get(2).end(), "last segment gets a default length");
    }

    @Test
    void dropsBlankTextAndNonFiniteValues() {
        List<TranscriptSegment> out = TranscriptNormalizer.normalizeWindow(List.of(
                new TranscriptSegment(1, 2, " "),
                new TranscriptSegment(Double.NaN, 3, "nan"),
                new TranscriptSegment(4, 5, "kept")), 0, null);

        assertEquals(1, out.size());
        assertEquals("kept", out.get(0).text());
    }

    @Test
    void mergeDropsSegmentsRepeatedAcrossWindowBoundary() {
        List<TranscriptSegment> merged = TranscriptNormalizer.merge(List.of(
                List.of(new TranscriptSegment(590, 600, "end of window 1")),
                List.of(new TranscriptSegment(592, 600, "same sentence again"),
                        new TranscriptSegment(600, 606, "start of window 2"))));

        assertEquals(List.of("end of window 1", "start of window 2"),
                merged.stream().map(TranscriptSegment::text).toList());
    }
}
