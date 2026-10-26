package pi.integrated.course.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pi.integrated.course.ai.GeminiClient;
import pi.integrated.course.ai.GeminiException;
import pi.integrated.course.config.CourseAiProperties;
import pi.integrated.course.config.CourseRagProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Window-by-window fallback used when the declared duration is missing or wrong. */
class LessonTranscriptionServiceTest {

    private final GeminiClient gemini = mock(GeminiClient.class);
    private final LessonTranscriptionService service = new LessonTranscriptionService(
            gemini, new CourseAiProperties(), new CourseRagProperties(), null, new ObjectMapper());

    @Test
    void unknownDurationAndTruncatedAnswerFallsBackToWindowsUntilSpeechStops() {
        when(gemini.generateContentInBackground(anyString(), any()))
                .thenThrow(new GeminiException("Gemini answer truncated (MAX_TOKENS)", 0))   // full video
                .thenReturn("[{\"start\":\"00:05\",\"end\":\"00:20\",\"text\":\"a\"},"
                        + "{\"start\":\"09:50\",\"end\":\"09:59\",\"text\":\"speech goes on\"}]") // 0-10 min
                .thenReturn("[{\"start\":\"10:02\",\"end\":\"12:00\",\"text\":\"b\"}]");        // 10-20 min, stops

        List<TranscriptSegment> segments = service.transcribe("dQw4w9WgXcQ", null);

        assertEquals(List.of("a", "speech goes on", "b"), segments.stream().map(TranscriptSegment::text).toList());
        verify(gemini, times(3)).generateContentInBackground(anyString(), any());
    }

    @Test
    void wrongDeclaredDurationFallsBackToWindows() {
        // Declared 3 min, but timestamps go to 7 min: rejected as out of range, then windowed.
        when(gemini.generateContentInBackground(anyString(), any()))
                .thenReturn("[{\"start\":\"00:10\",\"end\":\"00:20\",\"text\":\"a\"},"
                        + "{\"start\":\"05:00\",\"end\":\"05:10\",\"text\":\"b\"},"
                        + "{\"start\":\"07:00\",\"end\":\"07:10\",\"text\":\"c\"}]")
                .thenReturn("[{\"start\":\"00:10\",\"end\":\"00:20\",\"text\":\"a\"},"
                        + "{\"start\":\"05:00\",\"end\":\"05:10\",\"text\":\"b\"},"
                        + "{\"start\":\"07:00\",\"end\":\"07:10\",\"text\":\"c\"}]");

        List<TranscriptSegment> segments = service.transcribe("dQw4w9WgXcQ", 3);

        assertEquals(3, segments.size());
        assertEquals(420.0, segments.get(2).start());
        verify(gemini, times(2)).generateContentInBackground(anyString(), any());
    }

    @Test
    void shortVideoWithCorrectDurationNeedsOneCall() {
        when(gemini.generateContentInBackground(anyString(), any()))
                .thenReturn("[{\"start\":\"00:18\",\"end\":\"00:22\",\"text\":\"a\"}]");

        assertEquals(1, service.transcribe("dQw4w9WgXcQ", 4).size());
        verify(gemini, times(1)).generateContentInBackground(anyString(), any());
    }

    @Test
    void otherGeminiErrorsAreNotRetriedWindowByWindow() {
        when(gemini.generateContentInBackground(anyString(), any()))
                .thenThrow(new GeminiException("Gemini quota exhausted", 429));

        assertThrows(GeminiException.class, () -> service.transcribe("dQw4w9WgXcQ", null));
        verify(gemini, times(1)).generateContentInBackground(anyString(), any());
    }
}
