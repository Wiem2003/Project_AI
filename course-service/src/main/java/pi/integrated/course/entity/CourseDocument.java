package pi.integrated.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A PDF attached to a course and indexed by the RAG assistant. */
@Entity
@Table(name = "course_documents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_document_hash", columnNames = {"course_id", "sha256"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
