package pi.integrated.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pi.integrated.course.entity.CourseChunk;

import java.util.Collection;
import java.util.List;

@Repository
public interface CourseChunkRepository extends JpaRepository<CourseChunk, Long> {

    List<CourseChunk> findByCourseId(Long courseId);

    boolean existsByCourseIdAndSourceKeyAndSourceHash(Long courseId, String sourceKey, String sourceHash);

    long countByCourseId(Long courseId);

    @Query("select c.sourceType, count(c) from CourseChunk c where c.courseId = :courseId group by c.sourceType")
    List<Object[]> countBySourceType(@Param("courseId") Long courseId);

    @Modifying
    @Query("delete from CourseChunk c where c.courseId = :courseId and c.sourceKey = :sourceKey")
    int deleteSource(@Param("courseId") Long courseId, @Param("sourceKey") String sourceKey);

    @Modifying
    @Query("delete from CourseChunk c where c.courseId = :courseId and c.sourceKey not in :keepKeys")
    int deleteSourcesNotIn(@Param("courseId") Long courseId, @Param("keepKeys") Collection<String> keepKeys);

    @Modifying
    @Query("delete from CourseChunk c where c.courseId = :courseId")
    int deleteAllOfCourse(@Param("courseId") Long courseId);
}
