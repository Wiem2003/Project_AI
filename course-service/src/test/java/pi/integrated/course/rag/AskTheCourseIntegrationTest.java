package pi.integrated.course.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pi.integrated.course.ai.GeminiClient;
import pi.integrated.course.ai.GeminiClient.DocumentToEmbed;
import pi.integrated.course.dto.CourseAskResponse;
import pi.integrated.course.entity.Course;
import pi.integrated.course.entity.CourseModule;
import pi.integrated.course.entity.Lesson;
import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.repository.CourseChunkRepository;
import pi.integrated.course.repository.CourseModuleRepository;
import pi.integrated.course.repository.CourseRepository;
import pi.integrated.course.repository.LessonRepository;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end "Ask the course" with the real Spring context, H2, real security and a deterministic fake Gemini:
 * ingestion of a demo course (description, lesson, YouTube transcript, PDF) → question → exact citation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:course_rag;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=" + AskTheCourseIntegrationTest.JWT_SECRET,
        "course.ai.api-key=test-key",
        "course.ai.embedding-dimension=" + AskTheCourseIntegrationTest.DIM,
        "course.rag.chunk-target-chars=80",
        "course.rag.min-score=0.3",
        "course.rag.storage-dir=target/test-uploads"
})
class AskTheCourseIntegrationTest {

    static final String JWT_SECRET = "integration-test-secret-integration-test-secret";
    static final int DIM = 256;

    private static final String TRANSCRIPT_JSON = """
            [{"start": "00:00", "end": "00:05", "text": "Welcome to this lesson about English tenses."},
             {"start": "01:05.4", "end": "01:12", "text": "The present perfect is formed with have or has plus the past participle."},
             {"start": "02:10", "end": "02:18", "text": "We will now talk about the weather in London today."}]""";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseModuleRepository moduleRepository;
    @Autowired LessonRepository lessonRepository;
    @Autowired CourseChunkRepository chunkRepository;
    @Autowired CourseRagIngestionService ingestionService;

    @MockBean GeminiClient geminiClient;

    private final AtomicInteger transcriptionCalls = new AtomicInteger();
    private final AtomicInteger chatCalls = new AtomicInteger();

    private Long courseId;
    private Long lessonId;

    @BeforeEach
    void setUp() {
        when(geminiClient.embedDocuments(anyList())).thenAnswer(inv -> {
            List<DocumentToEmbed> docs = inv.getArgument(0);
            return docs.stream().map(d -> fakeEmbedding(d.text())).toList();
        });
        when(geminiClient.embedQuery(anyString())).thenAnswer(inv -> fakeEmbedding(inv.getArgument(0)));
        when(geminiClient.generateContentInBackground(anyString(), any())).thenAnswer(inv -> {
            transcriptionCalls.incrementAndGet();
            return TRANSCRIPT_JSON;
        });
        when(geminiClient.generateContent(anyString(), any())).thenAnswer(inv -> {
            chatCalls.incrementAndGet();
            // Fake LLM: answers from the best excerpt and cites it.
            return "{\"answer\": \"According to the course, see this excerpt [S1].\", \"insufficient\": false}";
        });

        Course course = courseRepository.save(Course.builder()
                .title("English tenses").category("Grammar").level("B1")
                .description("A short course.").build());
        CourseModule module = moduleRepository.save(CourseModule.builder()
                .course(course).title("Tenses").orderIndex(0).build());
        Lesson lesson = lessonRepository.save(Lesson.builder()
                .course(course).module(module).title("Present perfect").orderIndex(0)
                .description("Short introduction video.")
                .videoUrl("https://youtu.be/dQw4w9WgXcQ").durationMinutes(3).build());
        courseId = course.getId();
        lessonId = lesson.getId();
    }

    @Test
    void ingestsCourseAndAnswersWithExactCitations() throws Exception {
        // PDF upload (admin)
        mockMvc.perform(multipart("/api/courses/{id}/admin/documents", courseId)
                        .file(new MockMultipartFile("file", "verbs.pdf", "application/pdf", twoPagePdf()))
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isCreated());

        // First ingestion: everything is transcribed and embedded
        mockMvc.perform(post("/api/courses/{id}/admin/rag/reindex", courseId).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isAccepted());
        IngestionRun first = awaitIngestion();
        assertEquals(IngestionRun.State.DONE, first.getState(), () -> "errors: " + first.getErrors());
        assertEquals(1, transcriptionCalls.get());
        assertEquals(3, chunkRepository.findByCourseId(courseId).stream()
                .filter(c -> c.getSourceType() == RagSourceType.TRANSCRIPT).count(), "one chunk per segment");

        // Question about the video → citation of the transcript chunk, with its timestamp
        CourseAskResponse video = ask("How is the present perfect formed with have and the past participle?", lessonId);
        assertTrue(video.isAnswered());
        assertEquals("Leçon 1 – 01:05", video.getCitations().get(0).getLabel());
        assertEquals(65L, video.getCitations().get(0).getStartSeconds());
        assertEquals("/courses/" + courseId + "/lessons/" + lessonId + "?t=65", video.getCitations().get(0).getUrl());
        assertTrue(video.getAnswer().contains("[Leçon 1 – 01:05]"));

        // Question about the PDF → citation of the right page
        CourseAskResponse pdf = ask("What is the irregular participle of write, written?", null);
        assertEquals("PDF p. 2", pdf.getCitations().get(0).getLabel());

        // Off-topic question → refused without calling the LLM
        int chatBefore = chatCalls.get();
        CourseAskResponse offTopic = ask("Quelle est la capitale de l'Australie ?", null);
        assertFalse(offTopic.isAnswered());
        assertEquals(CourseAskService.NOT_FOUND_ANSWER, offTopic.getAnswer());
        assertEquals(chatBefore, chatCalls.get(), "LLM must not be called under the similarity threshold");

        // Second ingestion: idempotent, nothing re-transcribed nor re-embedded
        mockMvc.perform(post("/api/courses/{id}/admin/rag/reindex", courseId).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isAccepted());
        IngestionRun second = awaitIngestion();
        assertEquals(IngestionRun.State.DONE, second.getState());
        assertEquals(0, second.getSourcesIndexed());
        assertEquals(4, second.getSourcesUnchanged(), "course desc, lesson desc, video, pdf");
        assertEquals(1, transcriptionCalls.get(), "transcript served from cache");
    }

    @Test
    void protectsTheNewEndpoints() throws Exception {
        String body = "{\"question\":\"present perfect?\"}";
        mockMvc.perform(post("/api/courses/{id}/ask", courseId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/courses/{id}/ask", courseId).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/courses/{id}/admin/rag/status", courseId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/courses/{id}/admin/rag/status", courseId).header("Authorization", bearer("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/courses/{id}/ask", courseId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest());

        // Pre-existing endpoints stay public
        mockMvc.perform(get("/api/courses/{id}", courseId)).andExpect(status().isOk());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CourseAskResponse ask(String question, Long currentLessonId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("question", question,
                "lessonId", currentLessonId == null ? "" : currentLessonId));
        MvcResult result = mockMvc.perform(post("/api/courses/{id}/ask", courseId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                CourseAskResponse.class);
    }

    private IngestionRun awaitIngestion() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            IngestionRun run = ingestionService.status(courseId).getLastRun();
            if (run != null && !run.isActive()) {
                return run;
            }
            Thread.sleep(50);
        }
        fail("ingestion did not finish");
        return null;
    }

    private static String bearer(String role) {
        String token = Jwts.builder()
                .setSubject(role.toLowerCase(Locale.ROOT) + "@test.tn")
                .claim("role", role)
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }

    /** Deterministic bag-of-words vector: shared words ⇒ high cosine, no shared word ⇒ 0. */
    private static float[] fakeEmbedding(String text) {
        float[] v = new float[DIM];
        Matcher m = Pattern.compile("[a-z]+").matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            v[Math.floorMod(m.group().hashCode(), v.length)] += 1f;
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) Math.sqrt(norm);
            }
        }
        return v;
    }

    private static byte[] twoPagePdf() throws Exception {
        List<String> pages = new ArrayList<>(List.of(
                "Course handout. Table of contents.",
                "Irregular verbs: write written, eat eaten, go gone."));
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
