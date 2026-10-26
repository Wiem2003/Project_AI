package pi.integrated.course.rag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pi.integrated.course.ai.GeminiClient;
import pi.integrated.course.ai.GeminiClient.DocumentToEmbed;
import pi.integrated.course.config.CourseAiProperties;
import pi.integrated.course.config.CourseRagProperties;
import pi.integrated.course.dto.RagStatusResponse;
import pi.integrated.course.dto.RagStatusResponse.LessonTranscriptStatus;
import pi.integrated.course.entity.Course;
import pi.integrated.course.entity.CourseChunk;
import pi.integrated.course.entity.CourseDocument;
import pi.integrated.course.entity.Lesson;
import pi.integrated.course.entity.LessonTranscript;
import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.exception.CourseException;
import pi.integrated.course.repository.CourseChunkRepository;
import pi.integrated.course.repository.CourseRepository;
import pi.integrated.course.repository.LessonRepository;
import pi.integrated.course.repository.LessonTranscriptRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Builds the RAG index of a course from four sources: course description, lesson descriptions,
 * lesson video transcripts (Gemini) and course PDFs.
 * <p>
 * Idempotent: every source has a stable key and a content hash; a source is re-chunked and
 * re-embedded only when its hash changed, and chunks of removed sources are deleted.
 */
@Service
@RequiredArgsConstructor
public class CourseRagIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CourseRagIngestionService.class);

    /** Bump when chunking logic changes, to force re-embedding of every source. */
    private static final String CHUNKER_VERSION = "1";

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonTranscriptRepository transcriptRepository;
    private final CourseChunkRepository chunkRepository;
    private final CourseChunkStore chunkStore;
    private final CourseVectorIndex vectorIndex;
    private final CourseDocumentService documentService;
    private final LessonTranscriptionService transcriptionService;
    private final GeminiClient geminiClient;
    private final CourseAiProperties aiProperties;
    private final CourseRagProperties ragProperties;
    private final ThreadPoolTaskExecutor ragIngestionExecutor;

    private final Map<Long, IngestionRun> runs = new ConcurrentHashMap<>();
    /** Courses changed while their re-index was running: run again when it ends. */
    private final Set<Long> rerunRequested = ConcurrentHashMap.newKeySet();

    /** A chunk before embedding. */
    private record PendingChunk(RagSourceType type, Long lessonId, String lessonTitle, Integer lessonOrder,
                                Double startTime, Double endTime, Integer page, Long documentId,
                                String text, String embedTitle) {
    }

    // ── source keys ──────────────────────────────────────────────────────────

    static String courseKey(Long courseId) {
        return "course:" + courseId;
    }

    static String lessonDescKey(Long lessonId) {
        return "lesson:" + lessonId + ":desc";
    }

    static String lessonVideoKey(Long lessonId) {
        return "lesson:" + lessonId + ":video";
    }

    static String pdfKey(Long documentId) {
        return "pdf:" + documentId;
    }

    // ── job control ──────────────────────────────────────────────────────────

    /** Queues a re-index of the course; returns the running job if one is already in progress. */
    public IngestionRun startReindex(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new CourseException("Course not found: " + courseId);
        }
        if (!aiProperties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini API key missing: set the GEMINI_API_KEY environment variable");
        }
        boolean[] created = {false};
        IngestionRun run = runs.compute(courseId, (id, current) -> {
            if (current != null && current.isActive()) {
                return current;
            }
            created[0] = true;
            return new IngestionRun(id);
        });
        if (created[0]) {
            try {
                ragIngestionExecutor.execute(() -> runIngestion(run));
            } catch (TaskRejectedException e) {
                run.fail("Ingestion queue is full, try again later");
            }
        }
        return run;
    }

    /**
     * Automatic re-index after a content change. If a run is already in progress it may have read the course
     * before the change, so another run is scheduled for when it ends.
     */
    public void requestReindex(Long courseId) {
        IngestionRun current = runs.get(courseId);
        if (current != null && current.isActive()) {
            rerunRequested.add(courseId);
            return;
        }
        startReindex(courseId);
    }

    /** True while the course is being (re-)indexed, e.g. a new lesson video is being transcribed. */
    public boolean isIndexing(Long courseId) {
        IngestionRun run = runs.get(courseId);
        return (run != null && run.isActive()) || rerunRequested.contains(courseId);
    }

    public RagStatusResponse status(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new CourseException("Course not found: " + courseId);
        }
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (Object[] row : chunkRepository.countBySourceType(courseId)) {
            bySource.put(((RagSourceType) row[0]).getApiValue(), (Long) row[1]);
        }

        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(courseId);
        Map<Long, LessonTranscript> transcripts = transcriptRepository
                .findByLessonIdIn(lessons.stream().map(Lesson::getId).toList()).stream()
                .collect(Collectors.toMap(LessonTranscript::getLessonId, Function.identity()));

        List<LessonTranscriptStatus> lessonStatuses = new ArrayList<>();
        for (int i = 0; i < lessons.size(); i++) {
            Lesson l = lessons.get(i);
            LessonTranscript t = transcripts.get(l.getId());
            String status;
            if (l.getVideoUrl() == null || l.getVideoUrl().isBlank()) {
                status = "NO_VIDEO";
            } else if (YoutubeUrls.extractVideoId(l.getVideoUrl()).isEmpty()) {
                status = "NOT_YOUTUBE";
            } else if (t == null) {
                status = "NOT_STARTED";
            } else {
                status = t.getStatus().name();
            }
            lessonStatuses.add(LessonTranscriptStatus.builder()
                    .lessonId(l.getId())
                    .lessonOrder(i + 1)
                    .lessonTitle(l.getTitle())
                    .videoUrl(l.getVideoUrl())
                    .transcriptStatus(status)
                    .segmentCount(t == null ? null : t.getSegmentCount())
                    .error(t == null ? null : t.getErrorMessage())
                    .updatedAt(t == null ? null : t.getUpdatedAt())
                    .build());
        }

        return RagStatusResponse.builder()
                .courseId(courseId)
                .lastRun(runs.get(courseId))
                .chunkCount(chunkRepository.countByCourseId(courseId))
                .chunksBySourceType(bySource)
                .documentCount(documentService.findEntities(courseId).size())
                .lessons(lessonStatuses)
                .build();
    }

    // ── ingestion ────────────────────────────────────────────────────────────

    private void runIngestion(IngestionRun run) {
        Long courseId = run.getCourseId();
        run.start();
        log.info("RAG ingestion started for course {}", courseId);
        try {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new CourseException("Course not found: " + courseId));
            Set<String> keep = new HashSet<>();

            indexDescription(run, keep, courseKey(courseId), RagSourceType.COURSE_DESC,
                    null, null, null, course.getDescription(), course.getTitle());

            List<Lesson> lessons = lessonRepository.findByCourseIdOrderByModuleOrderIndexAscOrderIndexAsc(courseId);
            for (int i = 0; i < lessons.size(); i++) {
                Lesson lesson = lessons.get(i);
                int order = i + 1;
                indexDescription(run, keep, lessonDescKey(lesson.getId()), RagSourceType.LESSON_DESC,
                        lesson.getId(), lesson.getTitle(), order, lesson.getDescription(),
                        lessonLabel(order, lesson.getTitle()));
                indexVideo(run, keep, lesson, order);
            }

            for (CourseDocument doc : documentService.findEntities(courseId)) {
                indexPdf(run, keep, doc);
            }

            run.chunksDeleted(chunkStore.deleteSourcesNotIn(courseId, keep));
            run.finish();
            log.info("RAG ingestion of course {} finished: {} sources re-indexed, {} unchanged, {} chunks written, {} errors",
                    courseId, run.getSourcesIndexed(), run.getSourcesUnchanged(), run.getChunksWritten(),
                    run.getErrors().size());
        } catch (Exception e) {
            log.error("RAG ingestion of course {} failed", courseId, e);
            run.fail(e.getMessage());
        } finally {
            vectorIndex.evict(courseId);
            if (rerunRequested.remove(courseId)) {
                try {
                    startReindex(courseId);
                } catch (Exception e) {
                    log.warn("Follow-up re-index of course {} not started: {}", courseId, e.getMessage());
                }
            }
        }
    }

    private void indexDescription(IngestionRun run, Set<String> keep, String key, RagSourceType type,
                                  Long lessonId, String lessonTitle, Integer lessonOrder,
                                  String text, String embedTitle) {
        if (text == null || text.isBlank()) {
            return;
        }
        String hash = sourceHash(type, lessonOrder, lessonTitle, text);
        indexSource(run, keep, run.getCourseId(), key, hash, () ->
                TextChunker.split(text, ragProperties.getChunkTargetChars()).stream()
                        .map(t -> new PendingChunk(type, lessonId, lessonTitle, lessonOrder,
                                null, null, null, null, t, embedTitle))
                        .toList());
    }

    private void indexVideo(IngestionRun run, Set<String> keep, Lesson lesson, int order) {
        if (lesson.getVideoUrl() == null || lesson.getVideoUrl().isBlank()) {
            return;
        }
        String key = lessonVideoKey(lesson.getId());
        var transcript = transcriptionService.getOrTranscribe(
                lesson.getId(), lesson.getVideoUrl(), lesson.getDurationMinutes());
        if (transcript.isEmpty()) {
            if (YoutubeUrls.extractVideoId(lesson.getVideoUrl()).isPresent()) {
                run.error("Lesson " + order + " (" + lesson.getTitle() + "): transcription failed, see status");
            }
            return;
        }
        List<TranscriptSegment> segments = transcript.get().segments();
        String fingerprint = transcript.get().videoId() + "|" + segments.stream()
                .map(s -> s.start() + "|" + s.end() + "|" + s.text())
                .collect(Collectors.joining("\n"));
        String hash = sourceHash(RagSourceType.TRANSCRIPT, order, lesson.getTitle(), fingerprint);
        String embedTitle = lessonLabel(order, lesson.getTitle());

        indexSource(run, keep, run.getCourseId(), key, hash, () ->
                TranscriptChunker.chunk(segments, ragProperties.getChunkTargetChars()).stream()
                        .map(c -> new PendingChunk(RagSourceType.TRANSCRIPT, lesson.getId(), lesson.getTitle(),
                                order, c.start(), c.end(), null, null, c.text(), embedTitle))
                        .toList());
    }

    private void indexPdf(IngestionRun run, Set<String> keep, CourseDocument doc) {
        String hash = sourceHash(RagSourceType.PDF, null, doc.getOriginalFilename(), doc.getSha256());
        indexSource(run, keep, run.getCourseId(), pdfKey(doc.getId()), hash, () -> {
            try {
                List<PendingChunk> out = new ArrayList<>();
                for (var page : PdfTextExtractor.extractPages(documentService.fileOf(doc))) {
                    String title = doc.getOriginalFilename() + " p. " + page.pageNumber();
                    for (String t : TextChunker.split(page.text(), ragProperties.getChunkTargetChars())) {
                        out.add(new PendingChunk(RagSourceType.PDF, null, null, null,
                                null, null, page.pageNumber(), doc.getId(), t, title));
                    }
                }
                return out;
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read PDF " + doc.getOriginalFilename() + ": " + e.getMessage(), e);
            }
        });
    }

    /**
     * Re-chunks and re-embeds a source only if no chunk with the same hash exists yet.
     * On error the source is not kept, so its outdated chunks are removed at the end of the run.
     */
    private void indexSource(IngestionRun run, Set<String> keep, Long courseId, String key, String hash,
                             Supplier<List<PendingChunk>> chunker) {
        try {
            if (chunkRepository.existsByCourseIdAndSourceKeyAndSourceHash(courseId, key, hash)) {
                run.sourceUnchanged();
                keep.add(key);
                return;
            }
            List<PendingChunk> pending = chunker.get();
            if (pending.isEmpty()) {
                return;
            }

            List<float[]> vectors = new ArrayList<>(pending.size());
            int batchSize = Math.max(1, Math.min(100, ragProperties.getEmbeddingBatchSize()));
            for (int i = 0; i < pending.size(); i += batchSize) {
                List<DocumentToEmbed> batch = pending.subList(i, Math.min(pending.size(), i + batchSize)).stream()
                        .map(p -> new DocumentToEmbed(p.embedTitle(), p.text()))
                        .toList();
                vectors.addAll(geminiClient.embedDocuments(batch));
            }

            List<CourseChunk> chunks = new ArrayList<>(pending.size());
            for (int i = 0; i < pending.size(); i++) {
                PendingChunk p = pending.get(i);
                chunks.add(CourseChunk.builder()
                        .courseId(courseId)
                        .lessonId(p.lessonId())
                        .lessonTitle(p.lessonTitle())
                        .lessonOrder(p.lessonOrder())
                        .sourceType(p.type())
                        .sourceKey(key)
                        .sourceHash(hash)
                        .chunkIndex(i)
                        .startTime(p.startTime())
                        .endTime(p.endTime())
                        .page(p.page())
                        .documentId(p.documentId())
                        .text(p.text())
                        .embedding(EmbeddingCodec.toBytes(vectors.get(i)))
                        .embeddingModel(aiProperties.getEmbeddingModel())
                        .build());
            }
            chunkStore.replaceSource(courseId, key, chunks);
            run.sourceIndexed(chunks.size());
            keep.add(key);
        } catch (Exception e) {
            log.warn("RAG source {} of course {} failed: {}", key, courseId, e.getMessage());
            run.error(key + ": " + e.getMessage());
        }
    }

    /** Content + everything that changes the stored chunks (metadata, chunking and embedding config). */
    private String sourceHash(RagSourceType type, Integer lessonOrder, String title, String content) {
        return RagHashing.sha256(type, lessonOrder, title, content,
                aiProperties.getEmbeddingModel(), aiProperties.getEmbeddingDimension(),
                ragProperties.getChunkTargetChars(), CHUNKER_VERSION);
    }

    private static String lessonLabel(int order, String title) {
        return "Leçon " + order + " – " + (title == null ? "" : title);
    }
}
