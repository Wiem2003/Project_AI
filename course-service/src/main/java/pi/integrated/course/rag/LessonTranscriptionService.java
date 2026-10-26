package pi.integrated.course.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pi.integrated.course.ai.GeminiClient;
import pi.integrated.course.ai.GeminiException;
import pi.integrated.course.config.CourseAiProperties;
import pi.integrated.course.config.CourseRagProperties;
import pi.integrated.course.entity.LessonTranscript;
import pi.integrated.course.entity.TranscriptStatus;
import pi.integrated.course.repository.LessonTranscriptRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transcribes lesson YouTube videos with Gemini and caches the result per lesson.
 * A lesson is re-transcribed only when its video id changes or the previous attempt failed.
 */
@Service
@RequiredArgsConstructor
public class LessonTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(LessonTranscriptionService.class);

    private static final int WINDOW_MAX_OUTPUT_TOKENS = 16384;
    private static final int FULL_VIDEO_MAX_OUTPUT_TOKENS = 65536;
    /** Safety cap for videos of unknown length (3 h). */
    static final int MAX_VIDEO_SECONDS = 3 * 3600;
    /** If the last words of a window end this close to its end, the video probably goes on. */
    static final int SPEECH_CONTINUES_MARGIN = 20;

    private static final String SYSTEM_PROMPT =
            "You are a precise transcription engine. You only output the requested JSON.";

    /**
     * Timestamps are requested as "MM:SS" strings: in tests, Gemini placed MM:SS timestamps accurately,
     * whereas timestamps requested as numbers of seconds were often degenerate (all equal) or drifted past the end.
     */
    private static final Map<String, Object> SEGMENTS_SCHEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "start", Map.of("type", "STRING"),
                            "end", Map.of("type", "STRING"),
                            "text", Map.of("type", "STRING")),
                    "required", List.of("start", "end", "text"),
                    "propertyOrdering", List.of("start", "end", "text")));

    private final GeminiClient geminiClient;
    private final CourseAiProperties aiProperties;
    private final CourseRagProperties ragProperties;
    private final LessonTranscriptRepository transcriptRepository;
    private final ObjectMapper objectMapper;

    public record TranscriptResult(String videoId, List<TranscriptSegment> segments) {
    }

    /** Segment as returned by Gemini, timestamps as "MM:SS" / "H:MM:SS". */
    record RawSegment(String start, String end, String text) {
    }

    /**
     * @return the transcript, or empty when the lesson has no YouTube video or transcription failed
     *         (the failure is recorded on the LessonTranscript row).
     */
    public Optional<TranscriptResult> getOrTranscribe(Long lessonId, String videoUrl, Integer durationMinutes) {
        Optional<String> videoIdOpt = YoutubeUrls.extractVideoId(videoUrl);
        if (videoIdOpt.isEmpty()) {
            if (videoUrl != null && !videoUrl.isBlank()) {
                log.warn("Lesson {}: videoUrl is not a recognized YouTube link: {}", lessonId, videoUrl);
            }
            return Optional.empty();
        }
        String videoId = videoIdOpt.get();

        LessonTranscript row = transcriptRepository.findByLessonId(lessonId).orElse(null);
        if (row != null && row.getStatus() == TranscriptStatus.DONE && videoId.equals(row.getVideoId())) {
            return Optional.of(new TranscriptResult(videoId, readSegments(row.getSegmentsJson())));
        }

        if (row == null) {
            row = LessonTranscript.builder().lessonId(lessonId).build();
        }
        row.setVideoId(videoId);
        row.setStatus(TranscriptStatus.RUNNING);
        row.setErrorMessage(null);
        row.setModel(aiProperties.getTranscriptionModel());
        row = transcriptRepository.save(row);

        try {
            List<TranscriptSegment> segments = transcribe(videoId, durationMinutes);
            row.setStatus(TranscriptStatus.DONE);
            row.setSegmentsJson(objectMapper.writeValueAsString(segments));
            row.setSegmentCount(segments.size());
            transcriptRepository.save(row);
            log.info("Lesson {}: video {} transcribed ({} segments)", lessonId, videoId, segments.size());
            return Optional.of(new TranscriptResult(videoId, segments));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            row.setStatus(TranscriptStatus.FAILED);
            row.setErrorMessage(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
            transcriptRepository.save(row);
            log.warn("Lesson {}: transcription of video {} failed — {}", lessonId, videoId, msg);
            return Optional.empty();
        }
    }

    List<TranscriptSegment> transcribe(String videoId, Integer durationMinutes) {
        String url = YoutubeUrls.watchUrl(videoId);
        int window = Math.max(1, ragProperties.getTranscriptionWindowMinutes()) * 60;

        if (durationMinutes == null || durationMinutes * 60 <= window) {
            // Short video or unknown duration: one call. durationMinutes is rounded by the admin, hence +60 s.
            try {
                List<TranscriptSegment> raw = callGemini(url, null, null, FULL_VIDEO_MAX_OUTPUT_TOKENS);
                Double end = durationMinutes == null ? null : durationMinutes * 60.0 + 60;
                return TranscriptNormalizer.normalizeWindow(raw, 0, end);
            } catch (TranscriptNormalizer.InvalidTranscriptException | GeminiException e) {
                // Video longer than expected (answer truncated) or wrong duration (timestamps out of range):
                // transcribe it window by window instead, ignoring the declared duration.
                if (e instanceof GeminiException g && !g.getMessage().contains("MAX_TOKENS")) {
                    throw g;
                }
                log.info("Video {}: single-call transcription unusable ({}), switching to {}-minute windows",
                        videoId, e.getMessage(), window / 60);
                return transcribeWindows(url, window, 0);
            }
        }

        // durationMinutes is rounded by the admin: cover one extra minute to be safe.
        return transcribeWindows(url, window, durationMinutes * 60 + 60);
    }

    /**
     * Transcribes consecutive windows. Covers at least {@code minSeconds}, then continues while speech reaches
     * the end of the last window (declared duration too short or unknown), up to {@link #MAX_VIDEO_SECONDS}.
     */
    private List<TranscriptSegment> transcribeWindows(String url, int window, int minSeconds) {
        List<List<TranscriptSegment>> windows = new ArrayList<>();
        for (int start = 0; start < MAX_VIDEO_SECONDS; start += window) {
            int end = start + window;
            List<TranscriptSegment> raw = callGemini(url, start, end, WINDOW_MAX_OUTPUT_TOKENS);
            List<TranscriptSegment> segments = TranscriptNormalizer.normalizeWindow(raw, start, (double) end);
            windows.add(segments);
            boolean speechReachesEnd = !segments.isEmpty()
                    && segments.get(segments.size() - 1).end() >= end - SPEECH_CONTINUES_MARGIN;
            if (end >= minSeconds && !speechReachesEnd) {
                break;
            }
        }
        return TranscriptNormalizer.merge(windows);
    }

    private List<TranscriptSegment> callGemini(String url, Integer startSec, Integer endSec, int maxOutputTokens) {
        String clipInfo = startSec == null ? ""
                : String.format("%nOnly this clip of the video is provided: from %s to %s. Timestamps must be measured"
                        + " from the beginning of the full video, so they lie between %s and %s.",
                TimestampFormat.format(startSec), TimestampFormat.format(endSec),
                TimestampFormat.format(startSec), TimestampFormat.format(endSec));
        String instruction = """
                Transcribe all speech in this video verbatim, in the language it is spoken.
                Do not translate, summarize or describe the visuals.
                Return a JSON array of segments in chronological order. Each segment is one sentence
                or short phrase of at most about 15 seconds, with:
                - start: when the segment starts, as MM:SS (or H:MM:SS after one hour), as heard in the audio
                - end: when it ends, same format
                - text: the exact words spoken
                If nobody speaks, return [].""" + clipInfo;

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.0);
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", SEGMENTS_SCHEMA);
        generationConfig.put("mediaResolution", "MEDIA_RESOLUTION_LOW");
        generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));

        Map<String, Object> body = new HashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(GeminiClient.textPart(SYSTEM_PROMPT))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(
                        GeminiClient.youtubePart(url, startSec, endSec),
                        GeminiClient.textPart(instruction)))));
        body.put("generationConfig", generationConfig);

        String json = geminiClient.generateContentInBackground(aiProperties.getTranscriptionModel(), body);
        return parseGeminiSegments(stripCodeFence(json));
    }

    /** Converts Gemini's MM:SS segments to seconds; segments with an unreadable timestamp are dropped. */
    List<TranscriptSegment> parseGeminiSegments(String json) {
        final List<RawSegment> raw;
        try {
            raw = objectMapper.readValue(json, new TypeReference<List<RawSegment>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Invalid transcript JSON: " + e.getMessage(), e);
        }
        List<TranscriptSegment> out = new ArrayList<>(raw.size());
        for (RawSegment s : raw) {
            try {
                double start = TimestampFormat.parse(s.start());
                double end = s.end() == null || s.end().isBlank() ? start : TimestampFormat.parse(s.end());
                out.add(new TranscriptSegment(start, end, s.text()));
            } catch (IllegalArgumentException ignored) {
                // counted as missing by the normalizer's checks
            }
        }
        if (!raw.isEmpty() && out.size() < raw.size() * (1 - TranscriptNormalizer.MAX_INVALID_RATIO)) {
            throw new TranscriptNormalizer.InvalidTranscriptException(
                    (raw.size() - out.size()) + "/" + raw.size() + " segments with an unreadable timestamp");
        }
        return out;
    }

    private List<TranscriptSegment> readSegments(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TranscriptSegment>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Invalid transcript JSON: " + e.getMessage(), e);
        }
    }

    private static String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return t;
    }
}
