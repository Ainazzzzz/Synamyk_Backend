package synamyk.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import synamyk.entities.Question;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findBySubTestIdAndActiveTrueOrderByOrderIndexAsc(Long subTestId);
    List<Question> findBySubTestIdOrderByOrderIndexAsc(Long subTestId);
    long countBySubTestIdAndActiveTrue(Long subTestId);

    /** [subTestId, questionCount, totalPoints] of active questions of a test's sub-tests. */
    @org.springframework.data.jpa.repository.Query("SELECT q.subTest.id, COUNT(q), COALESCE(SUM(q.pointValue), 0) FROM Question q "
            + "WHERE q.subTest.test.id = :testId AND q.active = true GROUP BY q.subTest.id")
    List<Object[]> statsByTest(@org.springframework.data.repository.query.Param("testId") Long testId);
}