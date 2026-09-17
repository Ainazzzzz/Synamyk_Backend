package synamyk.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * «Пригласи друга»: created when an invited friend makes their first purchase.
 * The inviter redeems it for one test of their choice.
 */
@Entity
@Table(name = "referral_rewards", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"invitee_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ReferralReward extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    /** One reward per invited friend. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    /** Test unlocked with this reward; {@code null} while unused. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_test_id")
    private Test redeemedTest;

    @Column
    private LocalDateTime redeemedAt;

    public boolean isAvailable() {
        return redeemedAt == null;
    }
}
