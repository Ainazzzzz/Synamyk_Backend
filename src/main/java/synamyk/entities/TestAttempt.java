package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * One run of a whole test. Sections are taken one after another, each as a
 * {@link TestSession} linked back to this attempt; the result is one ОРТ score.
 */
@Entity
@Table(name = "test_attempts", indexes = {
        @Index(name = "idx_test_attempts_user_test", columnList = "user_id,test_id"),
        @Index(name = "idx_test_attempts_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TestAttempt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private Test test;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    /** 0-based position of the section the user is on (index into active sub-tests by levelOrder). */
    @Column(nullable = false)
    @Builder.Default
    private Integer currentSectionIndex = 0;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime pausedAt;

    @Column
    private LocalDateTime completedAt;

    /** Filled on finish. */
    @Column
    private Integer correctAnswers;

    @Column
    private Integer totalQuestions;

    @Column
    private Integer earnedPoints;

    @Column
    private Integer totalPoints;

    /** Final scaled ОРТ score (0..test.maxScore), filled on finish. */
    @Column
    private Integer ortScore;

    @Column
    private Integer maxScore;

    public enum AttemptStatus {
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        /** Replaced by «начать заново». */
        ABANDONED
    }
}
