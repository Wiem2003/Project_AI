package pi.integrated.course.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.server.ResponseStatusException;
import pi.integrated.course.ai.GeminiClient;
import pi.integrated.course.ai.GeminiClient.DocumentToEmbed;
import pi.integrated.course.config.CourseAiProperties;
import pi.integrated.course.config.CourseRagConfig;
import pi.integrated.course.config.CourseRagProperties;
import pi.integrated.course.dto.CourseAskResponse;
import pi.integrated.course.dto.CourseAskResponse.Citation;
import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.rag.CourseVectorIndex.IndexedCourseChunk;
import pi.integrated.course.rag.TranscriptChunker.TranscriptChunk;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evaluation of "Ask the course" against the REAL Gemini API, on a demo course (src/test/resources/rag-eval).
 * 7 in-course questions must cite the right lesson/timestamp or PDF page; 3 out-of-course questions must be refused.
 * <p>
 * Disabled by default (costs API quota). Run with:
 * {@code RAG_EVAL=true mvn test -Dtest=AskEvaluationTest} (key from GEMINI_API_KEY or course-service/.env).
 * A report is written to target/rag-eval-report.md.
 */
@EnabledIfEnvironmentVariable(named = "RAG_EVAL", matches = "true")
class AskEvaluationTest {

    private static final long COURSE_ID = 1L;
    private static final int CHUNK_TARGET = 300;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static CourseAiProperties ai;
    private static CourseRagProperties rag;
    private static GeminiClient gemini;
    private static List<IndexedCourseChunk> chunks;

    @BeforeAll
    static void indexDemoCourse() throws Exception {
        ai = new CourseAiProperties();
        ai.setApiKey(apiKey());
        String model = System.getenv("GEMINI_MODEL");
        if (model != null && !model.isBlank()) {
            ai.setChatModel(model);
            ai.setTranscriptionModel(model);
        }
        rag = new CourseRagProperties();
        rag.setChunkTargetChars(CHUNK_TARGET);
        gemini = new GeminiClient(ai, new CourseRagConfig().geminiRestClient(), MAPPER);

        JsonNode course = readJson("rag-eval/demo-course.json");
        List<IndexedCourseChunk> pending = new ArrayList<>();
        List<DocumentToEmbed> toEmbed = new ArrayList<>();
        long id = 1;

        for (String t : TextChunker.split(course.get("description").asText(), CHUNK_TARGET)) {
            pending.add(chunk(id++, null, null, null, RagSourceType.COURSE_DESC, null, null, null, t));
            toEmbed.add(new DocumentToEmbed(course.get("title").asText(), t));
        }
        int order = 0;
        for (JsonNode lesson : course.get("lessons")) {
            order++;
            long lessonId = lesson.get("id").asLong();
            String title = lesson.get("title").asText();
            String embedTitle = "Leçon " + order + " – " + title;
            for (String t : TextChunker.split(lesson.get("description").asText(), CHUNK_TARGET)) {
                pending.add(chunk(id++, lessonId, title, order, RagSourceType.LESSON_DESC, null, null, null, t));
                toEmbed.add(new DocumentToEmbed(embedTitle, t));
            }
            List<TranscriptSegment> segments = MAPPER.convertValue(lesson.get("segments"),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, TranscriptSegment.class));
            for (TranscriptChunk c : TranscriptChunker.chunk(segments, CHUNK_TARGET)) {
                pending.add(chunk(id++, lessonId, title, order, RagSourceType.TRANSCRIPT, c.start(), c.end(), null, c.text()));
                toEmbed.add(new DocumentToEmbed(embedTitle, c.text()));
            }
        }
        JsonNode pdf = course.get("pdf");
        for (JsonNode page : pdf.get("pages")) {
            for (String t : TextChunker.split(page.get("text").asText(), CHUNK_TARGET)) {
                pending.add(new IndexedCourseChunk(id++, COURSE_ID, null, null, null, RagSourceType.PDF, null, null,
                        page.get("page").asInt(), pdf.get("documentId").asLong(), t, null));
                toEmbed.add(new DocumentToEmbed(pdf.get("filename").asText() + " p. " + page.get("page").asInt(), t));
            }
        }

        List<float[]> vectors = gemini.embedDocuments(toEmbed);
        chunks = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            IndexedCourseChunk c = pending.get(i);
            chunks.add(new IndexedCourseChunk(c.chunkId(), c.courseId(), c.lessonId(), c.lessonTitle(), c.lessonOrder(),
                    c.sourceType(), c.startTime(), c.endTime(), c.page(), c.documentId(), c.text(), vectors.get(i)));
        }
    }

    @Test
    void answersFromTheCourseWithCorrectCitationsAndRefusesOutOfCourseQuestions() throws Exception {
        CourseVectorIndex index = new CourseVectorIndex(null, ai) {
            @Override
            public List<IndexedCourseChunk> chunksOf(Long courseId) {
                return chunks;
            }
        };
        CourseRetrievalService retrieval = new CourseRetrievalService(index, gemini, rag);
        CourseAskService askService = new CourseAskService(retrieval, gemini, ai, MAPPER, null);

        int inCourse = 0, inCourseOk = 0, outCourse = 0, outCourseOk = 0;
        double minInCourseSim = 1, maxOutCourseSim = 0;
        StringBuilder report = new StringBuilder("# Ask the course — evaluation\n\n")
                .append(String.format(Locale.ROOT, "min-score = %.2f, top-k = %d, chunk = %d chars, model = %s / %s%n%n",
                        rag.getMinScore(), rag.getTopK(), CHUNK_TARGET, ai.getChatModel(), ai.getEmbeddingModel()))
                .append("| # | Question | Best sim. | Expected | Got | OK |\n|---|---|---|---|---|---|\n");

        int n = 0;
        for (JsonNode q : readJson("rag-eval/questions.json")) {
            n++;
            String question = q.get("question").asText();
            boolean out = q.path("outOfCourse").asBoolean(false);
            double best = retrieval.retrieve(COURSE_ID, question, null).bestSimilarity();
            CourseAskResponse r;
            try {
                r = askService.ask(COURSE_ID, question, null);
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() != 429) throw e;
                Thread.sleep(61_000); // free-tier per-minute quota: wait for the next window, retry once
                r = askService.ask(COURSE_ID, question, null);
            }
            Set<String> got = new TreeSet<>(r.getCitations().stream().map(Citation::getLabel).toList());

            boolean ok;
            String expected;
            if (out) {
                outCourse++;
                maxOutCourseSim = Math.max(maxOutCourseSim, best);
                ok = !r.isAnswered();
                expected = "refusal";
                if (ok) outCourseOk++;
            } else {
                inCourse++;
                minInCourseSim = Math.min(minInCourseSim, best);
                Set<String> want = expectedLabels(q.get("expect"));
                ok = r.isAnswered() && got.stream().anyMatch(want::contains);
                expected = String.join(" or ", want);
                if (ok) inCourseOk++;
            }
            report.append(String.format(Locale.ROOT, "| %d | %s | %.3f | %s | %s | %s |%n", n, question, best, expected,
                    r.isAnswered() ? String.join(", ", got) : "refusal", ok ? "✅" : "❌"));
            report.append(String.format("|   | ↳ *%s* | | | | |%n", r.getAnswer().replace("\n", " ").replace("|", "/")));
            Thread.sleep(13_000); // free tier: only a few generateContent requests per minute
        }

        report.append(String.format(Locale.ROOT,
                "%nIn-course: %d/%d correct citations. Out-of-course: %d/%d refused.%n"
                        + "Lowest best-similarity of an in-course question: %.3f — highest of an out-of-course one: %.3f.%n",
                inCourseOk, inCourse, outCourseOk, outCourse, minInCourseSim, maxOutCourseSim));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/rag-eval-report.md"), report);
        System.out.println(report);

        assertEquals(outCourse, outCourseOk, "every out-of-course question must be refused");
        assertTrue(inCourseOk >= inCourse - 1, "at most one in-course miss: " + inCourseOk + "/" + inCourse);
    }

    /**
     * Real Gemini transcription of a public YouTube video (3:33, singing starts at about 0:18).
     * Checks the timestamps against the known video, not only their order.
     */
    @Test
    void transcribesARealYoutubeVideoWithAccurateTimestamps() {
        LessonTranscriptionService service = new LessonTranscriptionService(gemini, ai, rag, null, MAPPER);
        List<TranscriptSegment> segments = service.transcribe("dQw4w9WgXcQ", 4);

        assertTrue(segments.size() >= 15, "whole song transcribed: " + segments.size());
        TranscriptSegment first = segments.stream()
                .filter(s -> s.text().toLowerCase(Locale.ROOT).contains("strangers")).findFirst().orElseThrow();
        assertEquals(18, first.start(), 6, "first line is sung at about 0:18");
        double last = segments.get(segments.size() - 1).start();
        assertTrue(last > 170 && last < 214, "last line near the end of the 3:33 video, got " + last);
        long distinct = segments.stream().mapToLong(s -> (long) s.start()).distinct().count();
        assertTrue(distinct >= segments.size() * 0.8, "timestamps spread over the video");
        segments.stream().limit(6).forEach(s -> System.out.printf(Locale.ROOT, "%s  %s%n",
                TimestampFormat.format(s.start()), s.text()));
        System.out.printf("… %d segments, last at %s%n", segments.size(),
                TimestampFormat.format(segments.get(segments.size() - 1).start()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** "Leçon N – mm:ss" of the chunk containing second {@code at} of lesson N, or "PDF p. X". */
    private static Set<String> expectedLabels(JsonNode expect) {
        Set<String> labels = new TreeSet<>();
        for (JsonNode e : expect) {
            if (e.has("page")) {
                labels.add("PDF p. " + e.get("page").asInt());
                continue;
            }
            int lesson = e.get("lesson").asInt();
            double at = e.get("at").asDouble();
            chunks.stream()
                    .filter(c -> c.sourceType() == RagSourceType.TRANSCRIPT && c.lessonOrder() == lesson
                            && c.startTime() <= at && at < c.endTime())
                    .findFirst()
                    .ifPresent(c -> labels.add(CitationResolver.label(c)));
        }
        return labels;
    }

    private static IndexedCourseChunk chunk(long id, Long lessonId, String title, Integer order, RagSourceType type,
                                            Double start, Double end, Integer page, String text) {
        return new IndexedCourseChunk(id, COURSE_ID, lessonId, title, order, type, start, end, page, null, text, null);
    }

    private static JsonNode readJson(String resource) throws Exception {
        try (InputStream in = AskEvaluationTest.class.getClassLoader().getResourceAsStream(resource)) {
            return MAPPER.readTree(in);
        }
    }

    private static String apiKey() throws Exception {
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }
        Path dotEnv = Path.of(".env");
        if (Files.exists(dotEnv)) {
            for (String line : Files.readAllLines(dotEnv)) {
                if (line.startsWith("GEMINI_API_KEY=")) {
                    return line.substring("GEMINI_API_KEY=".length()).trim();
                }
            }
        }
        throw new IllegalStateException("GEMINI_API_KEY not set (env or course-service/.env)");
    }
}
