package pi.integrated.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cached Gemini transcription of a lesson video. Reused as long as the YouTube video id is unchanged.
 */
@Entity
@Table(name = "lesson_transcripts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", nullable = false, unique = true)
    private Long lessonId;

    @Column(name = "video_id", nullable = false, length = 32)
    private String videoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TranscriptStatus status;

    /** JSON array of {start, end, text} in seconds, see TranscriptSegment. */
    @Lob
    @Column(name = "segments_json", columnDefinition = "LONGTEXT")
    private String segmentsJson;

    @Column(name = "segment_count")
    private Integer segmentCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(length = 64)
    private String model;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
