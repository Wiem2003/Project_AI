package pi.integrated.course.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pi.integrated.course.dto.CourseAskRequest;
import pi.integrated.course.dto.CourseAskResponse;
import pi.integrated.course.entity.CourseDocument;
import pi.integrated.course.exception.CourseException;
import pi.integrated.course.rag.CourseAskService;
import pi.integrated.course.rag.CourseDocumentService;
import pi.integrated.course.repository.CourseRepository;
import pi.integrated.course.security.AskRateLimiter;
import pi.integrated.course.security.CourseAccessService;
import pi.integrated.course.security.JwtUserPrincipal;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Student side of "Ask the course": questions and the PDFs cited in answers. */
@RestController
@RequestMapping("/api/courses/{courseId}")
@RequiredArgsConstructor
public class CourseAskController {

    private final CourseAskService askService;
    private final CourseDocumentService documentService;
    private final CourseRepository courseRepository;
    private final CourseAccessService accessService;
    private final AskRateLimiter rateLimiter;

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@PathVariable Long courseId, @RequestBody CourseAskRequest request) {
        if (!courseRepository.existsById(courseId)) {
            throw new CourseException("Course not found: " + courseId);
        }
        JwtUserPrincipal user = accessService.currentUser();
        accessService.checkCanUseCourseAssistant(user, courseId);

        long retryAfter = rateLimiter.tryAcquire(user.getEmail());
        if (retryAfter > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                    .body(Map.of(
                            "status", 429,
                            "message", "Too many questions, try again in " + retryAfter + " s",
                            "retryAfterSeconds", retryAfter));
        }

        CourseAskResponse response = askService.ask(courseId, request.getQuestion(), request.getLessonId());
        return ResponseEntity.ok(response);
    }

    /** PDF of the course, opened from a [PDF p. X] citation (append #page=X on the client). */
    @GetMapping("/documents/{documentId}/file")
    public ResponseEntity<Resource> documentFile(@PathVariable Long courseId, @PathVariable Long documentId) {
        JwtUserPrincipal user = accessService.currentUser();
        accessService.checkCanUseCourseAssistant(user, courseId);

        CourseDocument doc = documentService.findEntity(courseId, documentId);
        File file = documentService.fileOf(doc);
        if (!file.isFile()) {
            throw new CourseException("PDF file missing on server for document " + documentId);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(doc.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file));
    }
}
