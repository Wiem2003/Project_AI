package pi.integrated.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pi.integrated.course.entity.LessonTranscript;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LessonTranscriptRepository extends JpaRepository<LessonTranscript, Long> {
    Optional<LessonTranscript> findByLessonId(Long lessonId);
    List<LessonTranscript> findByLessonIdIn(Collection<Long> lessonIds);
}
