package synamyk.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import synamyk.entities.UserGameRating;

import java.util.Optional;

@Repository
public interface UserGameRatingRepository extends JpaRepository<UserGameRating, Long> {

    Optional<UserGameRating> findByUserId(Long userId);

    @Query(value = "SELECT r FROM UserGameRating r WHERE r.gamesPlayed > 0 ORDER BY r.rating DESC, r.wins DESC, r.userId ASC",
            countQuery = "SELECT COUNT(r) FROM UserGameRating r WHERE r.gamesPlayed > 0")
    Page<UserGameRating> leaderboard(Pageable pageable);

    /** Number of players strictly above this rating (rank = this + 1). */
    @Query("SELECT COUNT(r) FROM UserGameRating r WHERE r.gamesPlayed > 0 AND r.rating > :rating")
    long countAbove(@Param("rating") int rating);
}
