package pi.integrated.course.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pi.integrated.course.config.CourseRagProperties;
import pi.integrated.course.dto.CourseDocumentResponse;
import pi.integrated.course.entity.CourseDocument;
import pi.integrated.course.exception.CourseException;
import pi.integrated.course.repository.CourseDocumentRepository;
import pi.integrated.course.repository.CourseRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** Stores the PDFs of a course on disk; their text is indexed by the next RAG re-index. */
@Service
@RequiredArgsConstructor
public class CourseDocumentService {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final CourseDocumentRepository documentRepository;
    private final CourseRepository courseRepository;
    private final CourseChunkStore chunkStore;
    private final CourseVectorIndex vectorIndex;
    private final CourseRagProperties ragProperties;

    @Transactional
    public CourseDocumentResponse upload(Long courseId, MultipartFile file) {
        if (!courseRepository.existsById(courseId)) {
            throw new CourseException("Course not found: " + courseId);
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF file is required");
        }
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file");
        }
        if (bytes.length < PDF_MAGIC.length
                || !Arrays.equals(Arrays.copyOf(bytes, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are accepted");
        }

        String sha256 = RagHashing.sha256(bytes);
        var existing = documentRepository.findByCourseIdAndSha256(courseId, sha256);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Path target = courseDir(courseId).resolve(sha256 + ".pdf");
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store PDF");
        }

        CourseDocument doc = CourseDocument.builder()
                .courseId(courseId)
                .originalFilename(safeFilename(file.getOriginalFilename()))
                .storagePath(target.toString())
                .sha256(sha256)
                .sizeBytes((long) bytes.length)
                .build();
        return toResponse(documentRepository.save(doc));
    }

    public List<CourseDocumentResponse> list(Long courseId) {
        return documentRepository.findByCourseIdOrderByUploadedAtAsc(courseId).stream()
                .map(this::toResponse).toList();
    }

    public List<CourseDocument> findEntities(Long courseId) {
        return documentRepository.findByCourseIdOrderByUploadedAtAsc(courseId);
    }

    public CourseDocument findEntity(Long courseId, Long documentId) {
        return documentRepository.findById(documentId)
                .filter(d -> d.getCourseId().equals(courseId))
                .orElseThrow(() -> new CourseException("Document " + documentId + " not found in course " + courseId));
    }

    @Transactional
    public void delete(Long courseId, Long documentId) {
        CourseDocument doc = findEntity(courseId, documentId);
        chunkStore.deleteSource(courseId, CourseRagIngestionService.pdfKey(documentId));
        vectorIndex.evict(courseId);
        documentRepository.delete(doc);
        try {
            Files.deleteIfExists(Paths.get(doc.getStoragePath()));
        } catch (IOException ignored) {
            // orphan file is harmless; the DB row is gone
        }
    }

    public File fileOf(CourseDocument doc) {
        return new File(doc.getStoragePath());
    }

    private Path courseDir(Long courseId) {
        return Paths.get(ragProperties.getStorageDir()).toAbsolutePath().resolve(String.valueOf(courseId));
    }

    private static String safeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "document.pdf";
        }
        String name = Paths.get(original.replace('\\', '/')).getFileName().toString();
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private CourseDocumentResponse toResponse(CourseDocument d) {
        return CourseDocumentResponse.builder()
                .id(d.getId())
                .courseId(d.getCourseId())
                .originalFilename(d.getOriginalFilename())
                .sizeBytes(d.getSizeBytes())
                .sha256(d.getSha256())
                .uploadedAt(d.getUploadedAt())
                .build();
    }
}
