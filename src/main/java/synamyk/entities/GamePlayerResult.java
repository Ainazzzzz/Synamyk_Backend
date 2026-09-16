package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_player_results")
@Getter
@Setter
@NoArgsConstructor
public class GamePlayerResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long gameTestId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Integer score = 0;

    @Column(nullable = false)
    private Integer totalQuestions = 0;

    @Column(nullable = false)
    private Boolean won = false;

    /** Game rating before / after this game ({@code null} on rows created before ratings existed). */
    @Column
    private Integer ratingBefore;

    @Column
    private Integer ratingAfter;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
