package pi.integrated.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDocumentResponse {
    private Long id;
    private Long courseId;
    private String originalFilename;
    private Long sizeBytes;
    private String sha256;
    private LocalDateTime uploadedAt;
}
