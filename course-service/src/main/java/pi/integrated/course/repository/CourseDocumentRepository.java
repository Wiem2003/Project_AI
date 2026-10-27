package pi.integrated.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pi.integrated.course.entity.CourseDocument;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseDocumentRepository extends JpaRepository<CourseDocument, Long> {
    List<CourseDocument> findByCourseIdOrderByUploadedAtAsc(Long courseId);
    Optional<CourseDocument> findByCourseIdAndSha256(Long courseId, String sha256);
}
