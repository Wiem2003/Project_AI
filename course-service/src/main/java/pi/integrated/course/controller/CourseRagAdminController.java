package pi.integrated.course.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pi.integrated.course.config.CourseRagProperties;
import pi.integrated.course.dto.CourseDocumentResponse;
import pi.integrated.course.dto.LessonQuizRequest;
import pi.integrated.course.dto.LessonQuizResponse;
import pi.integrated.course.rag.LessonQuizService;
import pi.integrated.course.dto.RagSearchResponse;
import pi.integrated.course.dto.RagStatusResponse;
import pi.integrated.course.rag.CourseDocumentService;
import pi.integrated.course.rag.CourseRagIngestionService;
import pi.integrated.course.rag.CourseRetrievalService;
import pi.integrated.course.rag.CourseRetrievalService.RetrievalResult;
import pi.integrated.course.rag.IngestionRun;

import java.util.List;

/** Admin side of "Ask the course": PDFs of the course and RAG (re-)indexing. */
@RestController
@RequestMapping("/api/courses/{courseId}/admin")
@RequiredArgsConstructor
public class CourseRagAdminController {

    private final CourseRagIngestionService ingestionService;
    private final CourseDocumentService documentService;
    private final CourseRetrievalService retrievalService;
    private final CourseRagProperties ragProperties;
    private final LessonQuizService lessonQuizService;

    /** Starts an asynchronous, idempotent re-index (only changed sources are re-transcribed / re-embedded). */
    @PostMapping("/rag/reindex")
    public ResponseEntity<IngestionRun> reindex(@PathVariable Long courseId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.startReindex(courseId));
    }

    @GetMapping("/rag/status")
    public ResponseEntity<RagStatusResponse> status(@PathVariable Long courseId) {
        return ResponseEntity.ok(ingestionService.status(courseId));
    }

    /** Retrieval only (no LLM call): shows passages and scores, used to calibrate course.rag.min-score. */
    @GetMapping("/rag/search")
    public ResponseEntity<RagSearchResponse> search(
            @PathVariable Long courseId,
            @RequestParam("q") String question,
            @RequestParam(value = "lessonId", required = false) Long lessonId) {
        RetrievalResult result = retrievalService.retrieve(courseId, question, lessonId);
        List<RagSearchResponse.Passage> passages = result.passages().stream()
                .map(r -> RagSearchResponse.Passage.builder()
                        .chunkId(r.chunk().chunkId())
                        .lessonId(r.chunk().lessonId())
                        .lessonOrder(r.chunk().lessonOrder())
                        .lessonTitle(r.chunk().lessonTitle())
                        .sourceType(r.chunk().sourceType())
                        .startTime(r.chunk().startTime())
                        .page(r.chunk().page())
                        .similarity(r.similarity())
                        .score(r.score())
                        .text(r.chunk().text())
                        .build())
                .toList();
        return ResponseEntity.ok(RagSearchResponse.builder()
                .question(question)
                .minScore(ragProperties.getMinScore())
                .bestSimilarity(result.bestSimilarity())
                .answerable(result.hasPassages())
                .passages(passages)
                .build());
    }

    /** Quiz generated from the indexed passages of one lesson; each question keeps its source passage. */
    @PostMapping("/rag/quiz")
    public ResponseEntity<LessonQuizResponse> generateLessonQuiz(@PathVariable Long courseId,
                                                                 @RequestBody LessonQuizRequest request) {
        return ResponseEntity.ok(lessonQuizService.generate(courseId, request));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseDocumentResponse> uploadDocument(
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.upload(courseId, file));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<CourseDocumentResponse>> listDocuments(@PathVariable Long courseId) {
        return ResponseEntity.ok(documentService.list(courseId));
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long courseId, @PathVariable Long documentId) {
        documentService.delete(courseId, documentId);
        return ResponseEntity.noContent().build();
    }
}
