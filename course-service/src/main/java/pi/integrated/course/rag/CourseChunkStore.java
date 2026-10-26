package pi.integrated.course.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pi.integrated.course.entity.CourseChunk;
import pi.integrated.course.repository.CourseChunkRepository;

import java.util.Collection;
import java.util.List;

/** Short write transactions for the ingestion job (Gemini calls stay outside any transaction). */
@Service
@RequiredArgsConstructor
public class CourseChunkStore {

    private final CourseChunkRepository chunkRepository;

    /** Atomically swaps all chunks of one source. */
    @Transactional
    public void replaceSource(Long courseId, String sourceKey, List<CourseChunk> chunks) {
        chunkRepository.deleteSource(courseId, sourceKey);
        chunkRepository.saveAll(chunks);
    }

    @Transactional
    public int deleteSource(Long courseId, String sourceKey) {
        return chunkRepository.deleteSource(courseId, sourceKey);
    }

    /** Removes chunks of sources that no longer exist (deleted lesson, removed video or PDF). */
    @Transactional
    public int deleteSourcesNotIn(Long courseId, Collection<String> keepKeys) {
        if (keepKeys.isEmpty()) {
            return chunkRepository.deleteAllOfCourse(courseId);
        }
        return chunkRepository.deleteSourcesNotIn(courseId, keepKeys);
    }
}
