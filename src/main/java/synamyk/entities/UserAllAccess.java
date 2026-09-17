package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import synamyk.enums.ProductCode;

import java.time.LocalDateTime;

/** «Купить все тесты» / «Открыть все тексты» grant. */
@Entity
@Table(name = "user_all_access", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "product"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserAllAccess extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCode product;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    /** {@code null} = permanent. */
    @Column
    private LocalDateTime expiresAt;
}
