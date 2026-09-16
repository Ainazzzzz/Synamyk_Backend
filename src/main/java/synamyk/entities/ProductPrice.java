package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import synamyk.enums.ProductCode;

import java.math.BigDecimal;

/** Admin-configurable price of a catalogue product («все тесты», «все тексты»). */
@Entity
@Table(name = "product_prices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductPrice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private ProductCode code;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    /** Crossed-out price for a promo badge, optional. */
    @Column(precision = 10, scale = 2)
    private BigDecimal oldPrice;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = false;
}
