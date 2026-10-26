package pi.integrated.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One indexed passage of a course (RAG "Ask the course").
 * All chunks produced from the same source share {@code sourceKey} and {@code sourceHash},
 * so a source is re-embedded only when its content (or the chunking/embedding config) changes.
 */
@Entity
@Table(name = "course_rag_chunks", indexes = {
        @Index(name = "idx_rag_chunk_course", columnList = "course_id"),
        @Index(name = "idx_rag_chunk_source", columnList = "course_id, source_key")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "lesson_title")
    private String lessonTitle;

    /** 1-based position of the lesson in the whole course ("Lecon N"). */
    @Column(name = "lesson_order")
    private Integer lessonOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private RagSourceType sourceType;

    /** e.g. "course:3", "lesson:12:desc", "lesson:12:video", "pdf:4". */
    @Column(name = "source_key", nullable = false, length = 64)
    private String sourceKey;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    /** Seconds from the start of the video (transcript chunks only). */
    @Column(name = "start_time")
    private Double startTime;

    @Column(name = "end_time")
    private Double endTime;

    /** 1-based PDF page (pdf chunks only). */
    private Integer page;

    /** Uploaded PDF id (pdf chunks only). */
    @Column(name = "document_id")
    private Long documentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    /** float32 little-endian vector, see EmbeddingCodec. */
    @Lob
    @Column(columnDefinition = "MEDIUMBLOB", nullable = false)
    private byte[] embedding;

    @Column(name = "embedding_model", nullable = false, length = 64)
    private String embeddingModel;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
