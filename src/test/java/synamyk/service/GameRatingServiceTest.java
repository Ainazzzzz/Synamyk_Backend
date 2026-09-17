package synamyk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import synamyk.entities.UserGameRating;
import synamyk.repo.UserGameRatingRepository;
import synamyk.repo.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRatingServiceTest {

    @Mock UserGameRatingRepository ratingRepository;
    @Mock UserRepository userRepository;
    @Mock MinioService minioService;

    @InjectMocks GameRatingService service;

    @Test
    void delta_equalRatings_winAndLossAreSymmetric() {
        int win = GameRatingService.delta(1000, 1000, 1.0, 50, false);
        int loss = GameRatingService.delta(1000, 1000, 0.0, 50, false);
        assertThat(win).isPositive();
        assertThat(loss).isEqualTo(-win);
    }

    @Test
    void delta_beatingStrongerOpponent_givesMore() {
        assertThat(GameRatingService.delta(1000, 1400, 1.0, 50, false))
                .isGreaterThan(GameRatingService.delta(1000, 600, 1.0, 50, false));
    }

    @Test
    void delta_winAlwaysAtLeastOne_lossAtMostMinusOne() {
        assertThat(GameRatingService.delta(2500, 100, 1.0, 50, true)).isGreaterThanOrEqualTo(1);
        assertThat(GameRatingService.delta(100, 2500, 0.0, 50, true)).isLessThanOrEqualTo(-1);
    }

    @Test
    void delta_botGamesCountLess() {
        assertThat(GameRatingService.delta(1000, 1000, 1.0, 50, true))
                .isLessThan(GameRatingService.delta(1000, 1000, 1.0, 50, false));
    }

    @Test
    void apply_loss_lowersRating_andResetsStreak() {
        UserGameRating r = UserGameRating.builder().userId(7L).rating(1000).winStreak(3).gamesPlayed(30).build();
        when(ratingRepository.findByUserId(7L)).thenReturn(Optional.of(r));
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameRatingService.Change change = service.apply(7L, 1000, 0.0, false);

        assertThat(change.after()).isLessThan(change.before());
        assertThat(r.getLosses()).isEqualTo(1);
        assertThat(r.getWinStreak()).isZero();
        assertThat(r.getGamesPlayed()).isEqualTo(31);
    }

    @Test
    void apply_neverBelowMinimum() {
        UserGameRating r = UserGameRating.builder().userId(7L).rating(GameRatingService.MIN_RATING).build();
        when(ratingRepository.findByUserId(7L)).thenReturn(Optional.of(r));
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.apply(7L, 100, 0.0, false).after()).isEqualTo(GameRatingService.MIN_RATING);
    }
}
