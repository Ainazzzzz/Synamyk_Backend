package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TestSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_test_id", nullable = false)
    private SubTest subTest;

    /** Full-test attempt this section session belongs to; {@code null} for a standalone sub-test run. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private TestAttempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    /** Index of the current question (0-based). */
    @Column(nullable = false)
    @Builder.Default
    private Integer currentIndex = 0;

    /** Number of correct answers — stored on finish for fast rating queries. */
    @Column(nullable = false)
    @Builder.Default
    private Integer correctAnswers = 0;

    /** Earned points (sum of pointValue for correct answers) — stored on finish. */
    @Column
    @Builder.Default
    private Integer earnedPoints = 0;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** Session expires at this time (startedAt + durationMinutes). */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column
    private LocalDateTime completedAt;

    /** Set when session is paused; used to extend expiresAt on resume. */
    @Column
    private LocalDateTime pausedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserAnswer> answers = new ArrayList<>();

    public enum SessionStatus {
        IN_PROGRESS,
        PAUSED,     // user interrupted (can resume)
        COMPLETED,
        ABANDONED,  // left permanently (kept for history)
        EXPIRED
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public long getRemainingSeconds() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiresAt)) return 0;
        return java.time.Duration.between(now, expiresAt).getSeconds();
    }
}