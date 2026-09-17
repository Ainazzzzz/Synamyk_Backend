package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import synamyk.enums.PaymentProduct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Bought test; {@code null} for catalogue products (ALL_TESTS / ALL_TEXTS). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id")
    private Test test;

    /** {@code null} on legacy rows — use {@link #resolveProduct()}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentProduct product;

    /**
     * Set when the payment buys a single sub-test. {@code null} = whole-test
     * bundle purchase (legacy behaviour). When set, {@code test} is the
     * sub-test's parent test (kept for reports and backward compatibility).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_test_id")
    private SubTest subTest;

    @Column(unique = true, nullable = false)
    private UUID paymentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 1000)
    private String paymentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(unique = true)
    private String transactionId;

    private String receiptNumber;

    @Column(columnDefinition = "TEXT")
    private String webhookData;

    private LocalDateTime paidAt;

    public PaymentProduct resolveProduct() {
        if (product != null) return product;
        return subTest != null ? PaymentProduct.SUB_TEST : PaymentProduct.TEST;
    }

    public enum PaymentStatus {
        PENDING,
        COMPLETED,
        EXPIRED,
        CANCELLED
    }
}