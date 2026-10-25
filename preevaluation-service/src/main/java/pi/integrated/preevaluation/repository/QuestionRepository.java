package pi.integrated.preevaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pi.integrated.preevaluation.EnglishLevel;
import pi.integrated.preevaluation.QuestionCategory;
import pi.integrated.preevaluation.entity.Question;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findBySourceHash(String sourceHash);

    @Query(
            value = "SELECT * FROM preeval_questions WHERE level = :level AND category = :category ORDER BY RAND() LIMIT :lim",
            nativeQuery = true
    )
    List<Question> pickRandom(
            @Param("level") String level,
            @Param("category") String category,
            @Param("lim") int lim
    );

    @Query("SELECT COUNT(q) FROM Question q WHERE q.level = :level AND q.category = :cat")
    long countByLevelAndCategory(@Param("level") EnglishLevel level, @Param("cat") QuestionCategory category);

    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.options WHERE q.id IN :ids")
    List<Question> findAllWithOptionsByIdIn(@Param("ids") Collection<Long> ids);
}
