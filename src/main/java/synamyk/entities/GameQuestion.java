package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "game_questions")
@Getter
@Setter
@NoArgsConstructor
public class GameQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_test_id", nullable = false)
    private GameTest gameTest;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column
    private String imageUrl;

    /** Validated figure JSON (coordinate plane / geometry drawing), see {@code FigureValidator}. */
    @Column(columnDefinition = "TEXT")
    private String figure;

    @Column(nullable = false)
    private Integer orderIndex = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "gameQuestion", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("orderIndex ASC")
    private List<GameAnswerOption> options = new ArrayList<>();

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
