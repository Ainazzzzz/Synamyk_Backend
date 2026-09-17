package synamyk.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import synamyk.entities.ReferralReward;

import java.util.List;

@Repository
public interface ReferralRewardRepository extends JpaRepository<ReferralReward, Long> {
    boolean existsByInviteeId(Long inviteeId);
    List<ReferralReward> findByInviterIdOrderByCreatedAtDesc(Long inviterId);
    List<ReferralReward> findByInviterIdAndRedeemedAtIsNullOrderByCreatedAtAsc(Long inviterId);
    long countByInviterId(Long inviterId);
}
