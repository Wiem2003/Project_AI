package pi.integrated.course.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "course.rag")
public class CourseRagProperties {

    /** Target chunk size in characters (a transcript segment is never cut, so a chunk may exceed it). */
    private int chunkTargetChars = 1000;

    /** Number of texts per Gemini batchEmbedContents call (API max: 100). */
    private int embeddingBatchSize = 50;

    /** Long videos are transcribed in windows of this length (requires Lesson.durationMinutes). */
    private int transcriptionWindowMinutes = 10;

    /** Directory where uploaded course PDFs are stored. */
    private String storageDir = "uploads/course-documents";

    /** Re-index a course automatically when one of its lessons, modules or its description changes. */
    private boolean autoReindex = true;

    /** Number of passages given to the LLM. */
    private int topK = 5;

    /**
     * Minimum cosine similarity (raw, before the lesson bonus) for a passage to be used.
     * If no passage reaches it, the LLM is not called.
     */
    private double minScore = 0.55;

    /** Ranking bonus for passages of the lesson the student is watching. */
    private double currentLessonBoost = 0.05;

    /** Max lesson passages given to the model for a quiz. */
    private int quizMaxPassages = 15;

    /** MMR trade-off between relevance to the lesson (1.0) and diversity (0.0) when choosing quiz passages. */
    private double quizMmrLambda = 0.5;

    /**
     * When true, students must have a COMPLETED payment for the course (checked in payment-service).
     * When false, any logged-in student can ask questions.
     */
    private boolean requireEnrollment = true;

    /** Max questions per user per sliding minute. */
    private int rateLimitPerMinute = 5;

    /** Max questions per user per sliding 24 hours. */
    private int rateLimitPerDay = 50;
}
