package pi.integrated.course.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import pi.integrated.course.ai.GeminiClient;
import pi.integrated.course.config.CourseAiProperties;
import pi.integrated.course.config.CourseRagProperties;
import pi.integrated.course.dto.LessonQuizRequest;
import pi.integrated.course.dto.LessonQuizResponse;
import pi.integrated.course.dto.LessonQuizResponse.Rejection;
import pi.integrated.course.entity.Course;
import pi.integrated.course.entity.Lesson;
import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.rag.CourseVectorIndex.IndexedCourseChunk;
import pi.integrated.course.repository.CourseRepository;
import pi.integrated.course.repository.LessonRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LessonQuizServiceTest {

    private static IndexedCourseChunk transcript(long id, long lessonId, double start, String text, float... vector) {
        return new IndexedCourseChunk(id, 1L, lessonId, "Daily routine", 1, RagSourceType.TRANSCRIPT,
                start, start + 30, null, null, text, vector);
    }

    private static final List<IndexedCourseChunk> EXCERPTS = List.of(
            transcript(1, 10, 141, "My alarm goes off at 6:30 a.m. and I jump out of bed."),
            transcript(2, 10, 510, "I try to read a paper book, not my phone, before sleeping."));

    private static JsonNode json(String s) throws Exception {
        return new ObjectMapper().readTree(s);
    }

    private static List<String> texts(List<LessonQuizResponse.Question> questions) {
        return questions.stream().map(LessonQuizResponse.Question::getText).toList();
    }

    private static List<String> reasons(List<Rejection> rejected) {
        return rejected.stream().map(Rejection::getReason).toList();
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    void keepsValidQuestionsAndAttachesTheirSource() throws Exception {
        var result = LessonQuizService.validateQuestions(json("""
                [{"source": "S1", "evidence": "My alarm goes off at 6:30 a.m.", "text": "What time does his alarm go off?",
                  "type": "MULTIPLE_CHOICE", "options": ["6:00 a.m.", "6:30 a.m.", "7:00 a.m.", "8:30 a.m."],
                  "correctAnswer": "6:30 A.M.", "explanation": "He says his alarm goes off at 6:30 a.m."},
                 {"source": "S2", "evidence": "I try to read a paper book, not my phone", "text": "She reads on her phone before sleeping.",
                  "type": "TRUE_FALSE", "options": ["Vrai", "Faux"], "correctAnswer": "false", "explanation": "She reads a paper book."}]
                """), EXCERPTS, 1L, 5);

        var questions = result.kept();
        assertEquals(2, questions.size());
        assertTrue(result.rejected().isEmpty());
        LessonQuizResponse.Question mcq = questions.get(0);
        assertEquals("6:30 a.m.", mcq.getCorrectAnswer(), "normalized to the exact option text");
        assertEquals("Leçon 1 – 02:21", mcq.getSource().getLabel());
        assertEquals("/courses/1/lessons/10?t=141", mcq.getSource().getUrl());
        assertEquals(EXCERPTS.get(0).text(), mcq.getSourceExcerpt());

        LessonQuizResponse.Question tf = questions.get(1);
        assertEquals(List.of("True", "False"), tf.getOptions());
        assertEquals("False", tf.getCorrectAnswer());
        assertEquals("Leçon 1 – 08:30", tf.getSource().getLabel());
    }

    @Test
    void rejectsMultipleChoiceWithoutExactlyFourDistinctOptionsOrWithoutTheAnswer() throws Exception {
        String evidence = "\"evidence\": \"My alarm goes off at 6:30 a.m.\"";
        var result = LessonQuizService.validateQuestions(json("""
                [{"source": "S1", %1$s, "text": "Three options?", "type": "MULTIPLE_CHOICE",
                  "options": ["a", "b", "c"], "correctAnswer": "a"},
                 {"source": "S1", %1$s, "text": "Five options?", "type": "MULTIPLE_CHOICE",
                  "options": ["a", "b", "c", "d", "e"], "correctAnswer": "a"},
                 {"source": "S1", %1$s, "text": "Duplicate options?", "type": "MULTIPLE_CHOICE",
                  "options": ["6:30", "6:30 ", "7:00", "8:00"], "correctAnswer": "7:00"},
                 {"source": "S1", %1$s, "text": "Empty option?", "type": "MULTIPLE_CHOICE",
                  "options": ["a", "", "c", "d"], "correctAnswer": "a"},
                 {"source": "S1", %1$s, "text": "Answer not in options?", "type": "MULTIPLE_CHOICE",
                  "options": ["a", "b", "c", "d"], "correctAnswer": "e"},
                 {"source": "S1", %1$s, "text": "Kept?", "type": "MULTIPLE_CHOICE",
                  "options": ["6:00", "6:30", "7:00", "8:00"], "correctAnswer": "6:30"}]
                """.formatted(evidence)), EXCERPTS, 1L, 5);

        assertEquals(List.of("Kept?"), texts(result.kept()));
        assertEquals(List.of("QCM : 3 options au lieu de 4", "QCM : 5 options au lieu de 4",
                "QCM : options vides ou en double", "QCM : options vides ou en double",
                "QCM : bonne réponse absente des options"), reasons(result.rejected()));
        assertEquals("Three options?", result.rejected().get(0).getQuestion());
    }

    @Test
    void rejectsInvalidTrueFalse() throws Exception {
        String evidence = "\"evidence\": \"I try to read a paper book\"";
        var result = LessonQuizService.validateQuestions(json("""
                [{"source": "S2", %1$s, "text": "No options?", "type": "TRUE_FALSE", "options": [], "correctAnswer": "True"},
                 {"source": "S2", %1$s, "text": "Three options?", "type": "TRUE_FALSE",
                  "options": ["True", "False", "Maybe"], "correctAnswer": "True"},
                 {"source": "S2", %1$s, "text": "Two True?", "type": "TRUE_FALSE", "options": ["True", "true"], "correctAnswer": "True"},
                 {"source": "S2", %1$s, "text": "Bad answer?", "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "Maybe"},
                 {"source": "S2", %1$s, "text": "Kept?", "type": "TRUE_FALSE", "options": ["False", "TRUE"], "correctAnswer": "True"}]
                """.formatted(evidence)), EXCERPTS, 1L, 5);

        assertEquals(List.of("Kept?"), texts(result.kept()));
        assertEquals(List.of("True", "False"), result.kept().get(0).getOptions());
        assertEquals(4, result.rejected().size());
        assertTrue(result.rejected().stream().allMatch(r -> r.getReason().startsWith("Vrai/Faux")));
    }

    @Test
    void mixedQuizValidatesEachQuestionWithItsOwnType() throws Exception {
        var result = LessonQuizService.validateQuestions(json("""
                [{"source": "S1", "evidence": "My alarm goes off at 6:30", "text": "MCQ with 2 options?",
                  "type": "MULTIPLE_CHOICE", "options": ["True", "False"], "correctAnswer": "True"},
                 {"source": "S1", "evidence": "My alarm goes off at 6:30", "text": "Alarm at 6:30?",
                  "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "True"}]
                """), EXCERPTS, 1L, 5);

        assertEquals(List.of("Alarm at 6:30?"), texts(result.kept()));
        assertEquals(List.of("QCM : 2 options au lieu de 4"), reasons(result.rejected()));
    }

    @Test
    void rejectsUnknownSourceAndDuplicates() throws Exception {
        String ok = "\"evidence\": \"My alarm goes off at 6:30\", \"type\": \"TRUE_FALSE\", \"options\": [\"True\", \"False\"], \"correctAnswer\": \"True\"";
        var result = LessonQuizService.validateQuestions(json("""
                [{"source": "S9", %1$s, "text": "Unknown source?"},
                 {"source": "S1", %1$s, "text": "Kept?"},
                 {"source": "S1", %1$s, "text": "kept?"}]
                """.formatted(ok)), EXCERPTS, 1L, 5);

        assertEquals(List.of("Kept?"), texts(result.kept()));
        assertEquals(List.of("étiquette de source invalide", "question en double"), reasons(result.rejected()));
    }

    @Test
    void rejectsEvidenceThatIsNotInTheCitedPassage() throws Exception {
        var result = LessonQuizService.validateQuestions(json("""
                [{"source": "S1", "evidence": "She reads a paper book before sleeping", "text": "Wrong passage quoted?",
                  "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "True"},
                 {"source": "S1", "evidence": "He wakes up very early every single morning", "text": "Invented quote?",
                  "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "True"},
                 {"source": "S1", "evidence": "alarm", "text": "Too short?",
                  "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "True"},
                 {"source": "S1", "text": "No evidence?",
                  "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "True"}]
                """), EXCERPTS, 1L, 5);

        assertTrue(result.kept().isEmpty());
        assertEquals(List.of("citation (evidence) absente du passage cité", "citation (evidence) absente du passage cité",
                "citation (evidence) absente ou hors 5 à 40 mots", "citation (evidence) absente ou hors 5 à 40 mots"),
                reasons(result.rejected()));
    }

    @Test
    void acceptsEvidenceDifferingOnlyByCaseOrPunctuation() throws Exception {
        var result = LessonQuizService.validateQuestions(json("""
                [{"source": "S1", "evidence": "MY ALARM GOES OFF AT 6 30 A M AND I JUMP", "text": "Alarm at 6:30?",
                  "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "True"},
                 {"source": "S2", "evidence": "i try to read a paper book... not my phone!", "text": "Paper book?",
                  "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "True"}]
                """), EXCERPTS, 1L, 5);

        assertEquals(2, result.kept().size());
    }

    @Test
    void keepsOnlyTheFirstRequestedValidQuestions() throws Exception {
        String ok = "\"evidence\": \"My alarm goes off at 6:30\", \"type\": \"TRUE_FALSE\", \"options\": [\"True\", \"False\"], \"correctAnswer\": \"True\"";
        var result = LessonQuizService.validateQuestions(json("""
                [{"source": "S1", %1$s, "text": "Q1"}, {"source": "S9", %1$s, "text": "Q2"},
                 {"source": "S1", %1$s, "text": "Q3"}, {"source": "S1", %1$s, "text": "Q4"}]
                """.formatted(ok)), EXCERPTS, 1L, 2);

        assertEquals(List.of("Q1", "Q3"), texts(result.kept()));
        assertEquals(1, result.rejected().size(), "questions after the first N valid ones are not checked");
    }

    @Test
    void levelMustBeCefrAndDefaultsToTheCourseLevel() {
        assertEquals("B2", LessonQuizService.resolveLevel("b2", "A1"));
        assertEquals("B1", LessonQuizService.resolveLevel(null, "b1"));
        assertNull(LessonQuizService.resolveLevel(null, "BEGINNER"));
        assertThrows(ResponseStatusException.class, () -> LessonQuizService.resolveLevel("Z9", null));
    }

    // ── generate(): topic, over-generation (Gemini mocked) ──────────────────

    private final GeminiClient gemini = mock(GeminiClient.class);
    private final CourseVectorIndex index = mock(CourseVectorIndex.class);

    private LessonQuizService service(List<IndexedCourseChunk> chunks) {
        Course course = Course.builder().id(1L).level("B1").build();
        Lesson lesson = Lesson.builder().id(10L).title("Daily routine").course(course).build();
        CourseRepository courses = mock(CourseRepository.class);
        LessonRepository lessons = mock(LessonRepository.class);
        when(courses.findById(1L)).thenReturn(Optional.of(course));
        when(lessons.findById(10L)).thenReturn(Optional.of(lesson));
        when(index.chunksOf(1L)).thenReturn(chunks);
        CourseAiProperties ai = new CourseAiProperties();
        ai.setApiKey("test-key");
        return new LessonQuizService(courses, lessons, index, gemini, ai, new CourseRagProperties(), new ObjectMapper());
    }

    private static final List<IndexedCourseChunk> LESSON = List.of(
            transcript(1, 10, 141, "My alarm goes off at 6:30 a.m. and I jump out of bed.", 1, 0, 0),
            transcript(2, 10, 300, "Breakfast is a bowl of cereal and a cup of coffee.", 0, 1, 0),
            transcript(3, 10, 510, "I try to read a paper book, not my phone, before sleeping.", 0, 0, 1));

    @Test
    void topicNotCoveredByTheLessonIsRefusedWith422WithoutCallingTheLlm() {
        LessonQuizService service = service(LESSON);
        when(gemini.embedQuery("football")).thenReturn(new float[]{-1, -1, -1});

        LessonQuizRequest request = new LessonQuizRequest(10L, 3, null, "MULTIPLE_CHOICE", "football");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.generate(1L, request));

        assertEquals(422, e.getStatusCode().value());
        assertEquals("Ce thème n'est pas traité dans cette leçon.", e.getReason());
        verify(gemini, never()).generateContentWithKey(anyString(), anyString(), any());
    }

    @Test
    void asksForThreeExtraQuestionsAndKeepsTheRequestedNumber() throws Exception {
        LessonQuizService service = service(LESSON);
        StringBuilder items = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            items.append(i > 1 ? "," : "").append("""
                    {"source": "S1", "evidence": "My alarm goes off at 6:30", "text": "Q%d",
                     "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "True"}""".formatted(i));
        }
        when(gemini.generateContentWithKey(anyString(), anyString(), any()))
                .thenReturn("{\"title\": \"T\", \"description\": \"D\", \"questions\": [" + items + "]}");

        LessonQuizResponse response = service.generate(1L, new LessonQuizRequest(10L, 2, null, "TRUE_FALSE", null));

        verify(gemini).generateContentWithKey(eq("test-key"), anyString(),
                argThat((Map<String, Object> body) -> body.toString().contains("Write 5 questions.")));
        assertEquals(List.of("Q1", "Q2"), texts(response.getQuestions()));
        assertEquals(2, response.getRequested());
        assertEquals(5, response.getGenerated());
        assertEquals("all", response.getSelectionMode());
        assertTrue(response.getRejected().isEmpty());
    }

    @Test
    void topicCoveredByTheLessonUsesOnlyTheMatchingPassages() throws Exception {
        LessonQuizService service = service(LESSON);
        when(gemini.embedQuery("morning")).thenReturn(new float[]{1, 0.9f, 0});
        when(gemini.generateContentWithKey(anyString(), anyString(), any())).thenReturn("""
                {"title": "T", "description": "D", "questions": [{"source": "S2", "evidence": "Breakfast is a bowl of cereal",
                 "text": "Cereal for breakfast?", "type": "TRUE_FALSE", "options": ["True", "False"], "correctAnswer": "True"}]}""");

        LessonQuizResponse response = service.generate(1L, new LessonQuizRequest(10L, 1, null, "TRUE_FALSE", "morning"));

        assertEquals("topic", response.getSelectionMode());
        assertEquals("Leçon 1 – 05:00", response.getQuestions().get(0).getSource().getLabel(), "S2 = 2nd passage kept");
        verify(gemini).generateContentWithKey(anyString(), anyString(), argThat((Map<String, Object> body) ->
                body.toString().contains("about this topic: morning") && !body.toString().contains("paper book")));
    }
}
