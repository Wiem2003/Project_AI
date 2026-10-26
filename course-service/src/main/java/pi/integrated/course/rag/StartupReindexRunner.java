package pi.integrated.course.rag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pi.integrated.course.config.CourseAiProperties;
import pi.integrated.course.config.CourseRagProperties;
import pi.integrated.course.entity.Course;
import pi.integrated.course.repository.CourseRepository;

/**
 * At startup, re-indexes every course so lessons added or changed while the service was stopped
 * (or before automatic re-indexing existed) are not missed. Courses already up to date cost no Gemini call:
 * unchanged sources are skipped and transcripts are cached; only new videos or failed transcriptions are processed.
 */
@Component
@RequiredArgsConstructor
public class StartupReindexRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupReindexRunner.class);

    private final CourseRepository courseRepository;
    private final CourseRagIngestionService ingestionService;
    private final CourseAiProperties aiProperties;
    private final CourseRagProperties ragProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void reindexAllCourses() {
        if (!ragProperties.isAutoReindex() || !aiProperties.isConfigured()) {
            return;
        }
        int started = 0;
        for (Course course : courseRepository.findAll()) {
            try {
                ingestionService.requestReindex(course.getId());
                started++;
            } catch (Exception e) {
                log.warn("Startup re-index of course {} not started: {}", course.getId(), e.getMessage());
            }
        }
        log.info("Startup re-index queued for {} course(s)", started);
    }
}
