package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** Chess-style game rating (Elo): wins raise it, losses lower it. */
@Entity
@Table(name = "user_game_ratings", indexes = {
        @Index(name = "idx_user_game_ratings_rating", columnList = "rating")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserGameRating extends BaseEntity {

    public static final int INITIAL_RATING = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    @Builder.Default
    private Integer rating = INITIAL_RATING;

    @Column(nullable = false)
    @Builder.Default
    private Integer peakRating = INITIAL_RATING;

    @Column(nullable = false)
    @Builder.Default
    private Integer gamesPlayed = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer wins = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer losses = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer draws = 0;

    /** Current winning streak (resets on loss/draw). */
    @Column(nullable = false)
    @Builder.Default
    private Integer winStreak = 0;
}
