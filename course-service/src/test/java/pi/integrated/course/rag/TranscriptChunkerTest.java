package pi.integrated.course.rag;

import org.junit.jupiter.api.Test;
import pi.integrated.course.rag.TranscriptChunker.TranscriptChunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptChunkerTest {

    private static final List<TranscriptSegment> SEGMENTS = List.of(
            new TranscriptSegment(0.0, 4.2, "Hello everyone."),
            new TranscriptSegment(4.2, 9.8, "Today we study the present perfect."),
            new TranscriptSegment(9.8, 15.0, "It links the past and the present."),
            new TranscriptSegment(252.4, 260.0, "For example: I have lived here for ten years."));

    @Test
    void groupsSegmentsUpToTargetSizeAndKeepsStartOfFirstSegment() {
        List<TranscriptChunk> chunks = TranscriptChunker.chunk(SEGMENTS, 60);

        assertEquals(3, chunks.size());
        assertEquals(0.0, chunks.get(0).start());
        assertEquals(9.8, chunks.get(0).end());
        assertEquals("Hello everyone. Today we study the present perfect.", chunks.get(0).text());
        assertEquals(9.8, chunks.get(1).start());
        assertEquals(252.4, chunks.get(2).start());
        assertEquals(260.0, chunks.get(2).end());
    }

    @Test
    void neverCutsASegmentEvenWhenLongerThanTarget() {
        List<TranscriptChunk> chunks = TranscriptChunker.chunk(SEGMENTS, 10);

        assertEquals(SEGMENTS.size(), chunks.size());
        for (int i = 0; i < SEGMENTS.size(); i++) {
            assertEquals(SEGMENTS.get(i).text(), chunks.get(i).text());
            assertEquals(SEGMENTS.get(i).start(), chunks.get(i).start());
        }
    }

    @Test
    void largeTargetGivesOneChunkSpanningTheWholeVideo() {
        List<TranscriptChunk> chunks = TranscriptChunker.chunk(SEGMENTS, 10_000);

        assertEquals(1, chunks.size());
        assertEquals(0.0, chunks.get(0).start());
        assertEquals(260.0, chunks.get(0).end());
    }

    @Test
    void skipsBlankSegmentsAndHandlesEmptyInput() {
        assertTrue(TranscriptChunker.chunk(List.of(), 100).isEmpty());
        assertTrue(TranscriptChunker.chunk(null, 100).isEmpty());

        List<TranscriptChunk> chunks = TranscriptChunker.chunk(List.of(
                new TranscriptSegment(1, 2, "  "),
                new TranscriptSegment(3, 4, "Real words.")), 100);
        assertEquals(1, chunks.size());
        assertEquals(3.0, chunks.get(0).start(), "start must come from the first non-blank segment");
    }
}
