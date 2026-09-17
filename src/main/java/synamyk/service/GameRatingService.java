package synamyk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import synamyk.dto.game.GameRatingResponse;
import synamyk.entities.User;
import synamyk.entities.UserGameRating;
import synamyk.repo.UserGameRatingRepository;
import synamyk.repo.UserRepository;

/**
 * Elo rating for 1v1 games. A win against a stronger opponent gives more points, a loss
 * against a weaker one costs more. Games against the bot count with half the K-factor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameRatingService {

    static final int MIN_RATING = 100;
    private static final int K_NEWCOMER = 40;   // first 20 games: faster calibration
    private static final int K_REGULAR = 24;
    private static final int NEWCOMER_GAMES = 20;

    private final UserGameRatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;

    /** Expected score of A against B. */
    static double expected(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10, (ratingB - ratingA) / 400.0));
    }

    /**
     * Rating delta for a player. {@code score}: 1 win, 0.5 draw, 0 loss.
     * A win always gives at least +1 and a loss at least −1, so every game moves the rating.
     */
    static int delta(int rating, int opponentRating, double score, int gamesPlayed, boolean vsBot) {
        int k = gamesPlayed < NEWCOMER_GAMES ? K_NEWCOMER : K_REGULAR;
        if (vsBot) k = k / 2;
        int d = (int) Math.round(k * (score - expected(rating, opponentRating)));
        if (score == 1.0 && d < 1) d = 1;
        if (score == 0.0 && d > -1) d = -1;
        return d;
    }

    public UserGameRating getOrCreate(Long userId) {
        return ratingRepository.findByUserId(userId)
                .orElseGet(() -> ratingRepository.save(UserGameRating.builder().userId(userId).build()));
    }

    /** Current rating, 1000 for a player without games (does not create a row). */
    public int currentRating(Long userId) {
        return ratingRepository.findByUserId(userId).map(UserGameRating::getRating).orElse(UserGameRating.INITIAL_RATING);
    }

    public record Change(int before, int after) {
        public int delta() {
            return after - before;
        }
    }

    /**
     * Applies a finished game. {@code score}: 1 win, 0.5 draw, 0 loss.
     * {@code opponentRating} is the opponent's rating before the game (for the bot — its virtual rating).
     */
    @Transactional
    public synchronized Change apply(Long userId, int opponentRating, double score, boolean vsBot) {
        UserGameRating r = getOrCreate(userId);
        int before = r.getRating();
        int after = Math.max(MIN_RATING, before + delta(before, opponentRating, score, r.getGamesPlayed(), vsBot));
        r.setRating(after);
        r.setPeakRating(Math.max(r.getPeakRating(), after));
        r.setGamesPlayed(r.getGamesPlayed() + 1);
        if (score == 1.0) {
            r.setWins(r.getWins() + 1);
            r.setWinStreak(r.getWinStreak() + 1);
        } else if (score == 0.0) {
            r.setLosses(r.getLosses() + 1);
            r.setWinStreak(0);
        } else {
            r.setDraws(r.getDraws() + 1);
            r.setWinStreak(0);
        }
        ratingRepository.save(r);
        log.debug("Game rating: userId={}, {} -> {} (score={}, vsBot={})", userId, before, after, score, vsBot);
        return new Change(before, after);
    }

    @Transactional(readOnly = true)
    public GameRatingResponse me(Long userId, String lang) {
        UserGameRating r = ratingRepository.findByUserId(userId)
                .orElse(UserGameRating.builder().userId(userId).build());
        Long rank = r.getGamesPlayed() > 0 ? ratingRepository.countAbove(r.getRating()) + 1 : null;
        return toResponse(r, rank, userRepository.findById(userId).orElse(null), lang);
    }

    @Transactional(readOnly = true)
    public Page<GameRatingResponse> leaderboard(int page, int size, String lang) {
        PageRequest pr = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        // rank with ties: players with equal rating share the place
        return ratingRepository.leaderboard(pr).map(r -> toResponse(r, ratingRepository.countAbove(r.getRating()) + 1,
                userRepository.findById(r.getUserId()).orElse(null), lang));
    }

    // ===== leagues =====

    enum League {
        BRONZE(0, "Бронза", "Коло"),
        SILVER(1100, "Серебро", "Күмүш"),
        GOLD(1300, "Золото", "Алтын"),
        PLATINUM(1500, "Платина", "Платина"),
        DIAMOND(1700, "Алмаз", "Алмаз");

        final int from;
        final String ru, ky;

        League(int from, String ru, String ky) {
            this.from = from;
            this.ru = ru;
            this.ky = ky;
        }

        static League of(int rating) {
            League result = BRONZE;
            for (League l : values()) if (rating >= l.from) result = l;
            return result;
        }
    }

    private GameRatingResponse toResponse(UserGameRating r, Long rank, User u, String lang) {
        League league = League.of(r.getRating());
        League next = league.ordinal() + 1 < League.values().length ? League.values()[league.ordinal() + 1] : null;
        String name = u == null ? null
                : ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
        return GameRatingResponse.builder()
                .userId(r.getUserId())
                .fullName(name)
                .avatarUrl(u != null ? minioService.presign(u.getAvatarUrl()) : null)
                .rating(r.getRating())
                .peakRating(r.getPeakRating())
                .rank(rank)
                .gamesPlayed(r.getGamesPlayed())
                .wins(r.getWins())
                .losses(r.getLosses())
                .draws(r.getDraws())
                .winStreak(r.getWinStreak())
                .league(league.name())
                .leagueName("KY".equalsIgnoreCase(lang) ? league.ky : league.ru)
                .nextLeagueRating(next != null ? next.from : null)
                .build();
    }
}
