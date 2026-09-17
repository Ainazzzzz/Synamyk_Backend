package synamyk.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import synamyk.entities.TestAttempt;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TestAttemptRepository extends JpaRepository<TestAttempt, Long> {

    /** IN_PROGRESS or PAUSED attempts of a user on a test, newest first. */
    @Query("SELECT a FROM TestAttempt a WHERE a.user.id = :userId AND a.test.id = :testId "
            + "AND a.status IN ('IN_PROGRESS', 'PAUSED') ORDER BY a.createdAt DESC")
    List<TestAttempt> findResumable(@Param("userId") Long userId, @Param("testId") Long testId);

    /** [testId, attemptId] of every resumable attempt of the user. */
    @Query("SELECT a.test.id, a.id FROM TestAttempt a WHERE a.user.id = :userId "
            + "AND a.status IN ('IN_PROGRESS', 'PAUSED') ORDER BY a.createdAt ASC")
    List<Object[]> findResumableIdsByUser(@Param("userId") Long userId);

    @Query(value = "SELECT a FROM TestAttempt a JOIN FETCH a.test t WHERE a.user.id = :userId "
            + "AND a.status = 'COMPLETED' ORDER BY a.completedAt DESC",
            countQuery = "SELECT COUNT(a) FROM TestAttempt a WHERE a.user.id = :userId AND a.status = 'COMPLETED'")
    Page<TestAttempt> findCompletedByUser(@Param("userId") Long userId, Pageable pageable);

    /** [testId, bestOrtScore, completedCount] per test for a user. */
    @Query("SELECT a.test.id, MAX(a.ortScore), COUNT(a) FROM TestAttempt a "
            + "WHERE a.user.id = :userId AND a.status = 'COMPLETED' GROUP BY a.test.id")
    List<Object[]> findBestScoresByUser(@Param("userId") Long userId);

    // ===== School / district rating =====

    /**
     * Per school: [schoolId, studentsWithResult, avgBestScore]. Best = max ОРТ score of each student.
     */
    @Query(value = """
            SELECT b.school_id, COUNT(*) AS students, AVG(b.best) AS avg_best
            FROM (SELECT u.school_id, u.id, MAX(a.ort_score) AS best
                  FROM test_attempts a JOIN users u ON u.id = a.user_id
                  JOIN schools s ON s.id = u.school_id
                  WHERE a.status = 'COMPLETED' AND s.district_id = :districtId
                  GROUP BY u.school_id, u.id) b
            GROUP BY b.school_id
            """, nativeQuery = true)
    List<Object[]> schoolScores(@Param("districtId") Long districtId);

    /** Per school: [schoolId, completedAttemptsSince]. */
    @Query(value = """
            SELECT u.school_id, COUNT(a.id)
            FROM test_attempts a JOIN users u ON u.id = a.user_id
            JOIN schools s ON s.id = u.school_id
            WHERE a.status = 'COMPLETED' AND a.completed_at >= :since AND s.district_id = :districtId
            GROUP BY u.school_id
            """, nativeQuery = true)
    List<Object[]> schoolActivity(@Param("districtId") Long districtId, @Param("since") LocalDateTime since);

    /**
     * Per school: [schoolId, avgGrowth] — for students with results both before and after
     * {@code since}: best after minus best before.
     */
    @Query(value = """
            SELECT g.school_id, AVG(g.after_best - g.before_best)
            FROM (SELECT u.school_id, u.id,
                         MAX(CASE WHEN a.completed_at <  :since THEN a.ort_score END) AS before_best,
                         MAX(CASE WHEN a.completed_at >= :since THEN a.ort_score END) AS after_best
                  FROM test_attempts a JOIN users u ON u.id = a.user_id
                  JOIN schools s ON s.id = u.school_id
                  WHERE a.status = 'COMPLETED' AND s.district_id = :districtId
                  GROUP BY u.school_id, u.id) g
            WHERE g.before_best IS NOT NULL AND g.after_best IS NOT NULL
            GROUP BY g.school_id
            """, nativeQuery = true)
    List<Object[]> schoolGrowth(@Param("districtId") Long districtId, @Param("since") LocalDateTime since);

    /** Per district of a region: [districtId, studentsWithResult, avgBestScore]. */
    @Query(value = """
            SELECT b.district_id, COUNT(*), AVG(b.best)
            FROM (SELECT s.district_id, u.id, MAX(a.ort_score) AS best
                  FROM test_attempts a JOIN users u ON u.id = a.user_id
                  JOIN schools s ON s.id = u.school_id
                  JOIN districts d ON d.id = s.district_id
                  WHERE a.status = 'COMPLETED' AND d.region_id = :regionId
                  GROUP BY s.district_id, u.id) b
            GROUP BY b.district_id
            """, nativeQuery = true)
    List<Object[]> districtScores(@Param("regionId") Long regionId);

    /**
     * Student leaderboard by best ОРТ score: [userId, firstName, lastName, avatarUrl, schoolName, best].
     * Scope filters are optional (null = no filter).
     */
    @Query(value = """
            SELECT u.id, u.first_name, u.last_name, u.avatar_url, s.name, MAX(a.ort_score) AS best
            FROM test_attempts a JOIN users u ON u.id = a.user_id
            LEFT JOIN schools s ON s.id = u.school_id
            LEFT JOIN districts d ON d.id = s.district_id
            WHERE a.status = 'COMPLETED' AND u.active = TRUE
              AND (CAST(:schoolId AS BIGINT) IS NULL OR u.school_id = :schoolId)
              AND (CAST(:districtId AS BIGINT) IS NULL OR s.district_id = :districtId)
              AND (CAST(:regionId AS BIGINT) IS NULL OR u.region_id = :regionId)
            GROUP BY u.id, u.first_name, u.last_name, u.avatar_url, s.name
            ORDER BY best DESC, u.id ASC
            LIMIT :lim
            """, nativeQuery = true)
    List<Object[]> studentLeaderboard(@Param("schoolId") Long schoolId,
                                      @Param("districtId") Long districtId,
                                      @Param("regionId") Long regionId,
                                      @Param("lim") int limit);
}
