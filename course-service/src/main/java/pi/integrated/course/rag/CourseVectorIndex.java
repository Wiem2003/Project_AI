package pi.integrated.course.rag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pi.integrated.course.config.CourseAiProperties;
import pi.integrated.course.entity.CourseChunk;
import pi.integrated.course.entity.RagSourceType;
import pi.integrated.course.repository.CourseChunkRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of the chunk vectors of each course, loaded lazily from course_rag_chunks
 * and evicted when the course is re-indexed.
 */
@Component
@RequiredArgsConstructor
public class CourseVectorIndex {

    private static final Logger log = LoggerFactory.getLogger(CourseVectorIndex.class);

    private final CourseChunkRepository chunkRepository;
    private final CourseAiProperties aiProperties;

    private final Map<Long, List<IndexedCourseChunk>> byCourse = new ConcurrentHashMap<>();

    public record IndexedCourseChunk(Long chunkId, Long courseId, Long lessonId, String lessonTitle,
                                     Integer lessonOrder, RagSourceType sourceType, Double startTime,
                                     Double endTime, Integer page, Long documentId, String text,
                                     float[] vector) {
    }

    public List<IndexedCourseChunk> chunksOf(Long courseId) {
        return byCourse.computeIfAbsent(courseId, this::load);
    }

    public void evict(Long courseId) {
        byCourse.remove(courseId);
    }

    private List<IndexedCourseChunk> load(Long courseId) {
        String model = aiProperties.getEmbeddingModel();
        int dim = aiProperties.getEmbeddingDimension();
        List<IndexedCourseChunk> out = new ArrayList<>();
        int skipped = 0;
        for (CourseChunk c : chunkRepository.findByCourseId(courseId)) {
            float[] vector = EmbeddingCodec.fromBytes(c.getEmbedding());
            // Vectors from another model/dimension are not comparable with the query vector.
            if (!model.equals(c.getEmbeddingModel()) || vector.length != dim) {
                skipped++;
                continue;
            }
            out.add(new IndexedCourseChunk(c.getId(), c.getCourseId(), c.getLessonId(), c.getLessonTitle(),
                    c.getLessonOrder(), c.getSourceType(), c.getStartTime(), c.getEndTime(), c.getPage(),
                    c.getDocumentId(), c.getText(), vector));
        }
        if (skipped > 0) {
            log.warn("Course {}: {} chunks ignored (embedding model/dimension changed) — re-index the course",
                    courseId, skipped);
        }
        return List.copyOf(out);
    }
}
