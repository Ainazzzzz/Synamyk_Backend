package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import synamyk.enums.QuestionType;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Question extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_test_id", nullable = false)
    private SubTest subTest;

    /**
     * Optional section name, e.g. "1-часть: Математика"
     */
    @Column
    private String sectionName;

    @Column
    private String sectionNameKy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String textKy;

    @Column
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'STANDARD'")
    @Builder.Default
    private QuestionType questionType = QuestionType.STANDARD;

    /** «Колонка А» for {@link QuestionType#COMPARISON}. May contain LaTeX. */
    @Column(columnDefinition = "TEXT")
    private String columnA;

    @Column(columnDefinition = "TEXT")
    private String columnAKy;

    /** «Колонка Б» for {@link QuestionType#COMPARISON}. May contain LaTeX. */
    @Column(columnDefinition = "TEXT")
    private String columnB;

    @Column(columnDefinition = "TEXT")
    private String columnBKy;

    /** Validated figure JSON (coordinate plane / geometry drawing), see {@code FigureValidator}. */
    @Column(columnDefinition = "TEXT")
    private String figure;

    /** Reading passage this question belongs to («Окуу жана түшүнүү»), {@code null} otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passage_id")
    private ReadingPassage passage;

    /**
     * Stored explanation used as fallback / context for AI analysis.
     */
    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(columnDefinition = "TEXT")
    private String explanationKy;

    @Column(nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer pointValue = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<AnswerOption> options = new ArrayList<>();
}