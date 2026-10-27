package pi.integrated.course.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups consecutive transcript segments into chunks of about {@code targetChars} characters.
 * A segment is never cut: a chunk keeps the start time of its first segment and the end time of its last.
 */
public final class TranscriptChunker {

    private TranscriptChunker() {
    }

    public record TranscriptChunk(double start, double end, String text) {
    }

    public static List<TranscriptChunk> chunk(List<TranscriptSegment> segments, int targetChars) {
        List<TranscriptChunk> out = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return out;
        }
        int target = Math.max(1, targetChars);

        StringBuilder text = new StringBuilder();
        double start = 0;
        double end = 0;
        for (TranscriptSegment seg : segments) {
            String segText = seg.text() == null ? "" : seg.text().trim();
            if (segText.isEmpty()) {
                continue;
            }
            if (text.length() > 0 && text.length() + 1 + segText.length() > target) {
                out.add(new TranscriptChunk(start, end, text.toString()));
                text.setLength(0);
            }
            if (text.length() == 0) {
                start = seg.start();
            } else {
                text.append(' ');
            }
            text.append(segText);
            end = Math.max(end, seg.end());
        }
        if (text.length() > 0) {
            out.add(new TranscriptChunk(start, end, text.toString()));
        }
        return out;
    }
}
