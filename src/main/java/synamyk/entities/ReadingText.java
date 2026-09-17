package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** Library item of the «Тексттер» tab: a practice reading text (inline and/or PDF). */
@Entity
@Table(name = "reading_texts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ReadingText extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column
    private String titleKy;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String contentKy;

    /** MinIO object key of the PDF. */
    @Column(length = 1000)
    private String pdfUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean free = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
