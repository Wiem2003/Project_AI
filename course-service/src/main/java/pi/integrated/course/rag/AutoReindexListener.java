package pi.integrated.course.rag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pi.integrated.course.config.CourseAiProperties;
import pi.integrated.course.config.CourseRagProperties;

/**
 * Re-indexes a course after its content changed (once the change is committed, so the job reads it).
 * Cheap when little changed: ingestion is idempotent and transcripts are cached per video.
 */
@Component
@RequiredArgsConstructor
public class AutoReindexListener {

    private static final Logger log = LoggerFactory.getLogger(AutoReindexListener.class);

    private final CourseRagIngestionService ingestionService;
    private final CourseAiProperties aiProperties;
    private final CourseRagProperties ragProperties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCourseContentChanged(CourseContentChangedEvent event) {
        if (!ragProperties.isAutoReindex() || !aiProperties.isConfigured() || event.courseId() == null) {
            return;
        }
        try {
            ingestionService.requestReindex(event.courseId());
        } catch (Exception e) {
            // never break the admin's edit because of the index
            log.warn("Auto re-index of course {} not started: {}", event.courseId(), e.getMessage());
        }
    }
}
