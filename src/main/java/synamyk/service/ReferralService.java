package synamyk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import synamyk.dto.referral.ReferralInfoResponse;
import synamyk.entities.*;
import synamyk.enums.PushCategory;
import synamyk.enums.PushDataType;
import synamyk.exception.AppException;
import synamyk.repo.*;
import synamyk.util.L10n;
import synamyk.util.PushMessages;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * «Пригласи друга»: a friend registers with your code; when they make their first purchase
 * you get a reward that unlocks any one test for free.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ReferralRewardRepository rewardRepository;
    private final PaymentRepository paymentRepository;
    private final TestRepository testRepository;
    private final SubTestRepository subTestRepository;
    private final UserTestAccessRepository userTestAccessRepository;
    private final AccessResolver accessResolver;
    private final PushNotificationService pushNotificationService;

    @Value("${app.invite-link-base:https://synamyk.kg/invite/}")
    private String inviteLinkBase;

    @Transactional
    public ReferralInfoResponse getInfo(Long userId, String lang) {
        User user = findUser(userId);
        String code = ensureCode(user);
        boolean ky = "KY".equalsIgnoreCase(lang);

        List<ReferralReward> rewards = rewardRepository.findByInviterIdOrderByCreatedAtDesc(userId);
        String link = inviteLinkBase + code;

        return ReferralInfoResponse.builder()
                .code(code)
                .inviteLink(link)
                .shareText(ky
                        ? "ЖРТга мени менен даярдан! Каттоодо менин кодумду жаз: " + code + " " + link
                        : "Готовься к ОРТ вместе со мной! При регистрации введи мой код: " + code + " " + link)
                .invitedCount(userRepository.countByReferredById(userId))
                .purchasedCount((long) rewards.size())
                .availableRewards((int) rewards.stream().filter(ReferralReward::isAvailable).count())
                .canApplyCode(canApplyCode(user))
                .referredByName(user.getReferredBy() != null ? displayName(user.getReferredBy()) : null)
                .rewards(rewards.stream().map(r -> ReferralInfoResponse.RewardEntry.builder()
                        .rewardId(r.getId())
                        .friendName(displayName(r.getInvitee()))
                        .earnedAt(r.getCreatedAt())
                        .available(r.isAvailable())
                        .redeemedTestId(r.getRedeemedTest() != null ? r.getRedeemedTest().getId() : null)
                        .redeemedTestTitle(r.getRedeemedTest() != null
                                ? L10n.pick(r.getRedeemedTest().getTitle(), r.getRedeemedTest().getTitleKy(), lang) : null)
                        .redeemedAt(r.getRedeemedAt())
                        .build()).toList())
                .build();
    }

    /** Link the current user to an inviter. Allowed once, and only before the first purchase. */
    @Transactional
    public ReferralInfoResponse apply(Long userId, String rawCode, String lang) {
        User user = findUser(userId);
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        User inviter = userRepository.findByReferralCode(code)
                .orElseThrow(() -> new AppException("Код приглашения не найден.", "Чакыруу коду табылган жок."));
        if (inviter.getId().equals(userId)) {
            throw new AppException("Нельзя использовать свой собственный код.", "Өз кодуңузду колдонууга болбойт.");
        }
        if (user.getReferredBy() != null) {
            throw new AppException("Код приглашения уже применён.", "Чакыруу коду мурун колдонулган.");
        }
        if (!canApplyCode(user)) {
            throw new AppException(
                    "Код можно ввести только до первой покупки.",
                    "Кодду биринчи сатып алууга чейин гана киргизүүгө болот.");
        }
        if (inviter.getReferredBy() != null && inviter.getReferredBy().getId().equals(userId)) {
            throw new AppException("Нельзя пригласить друг друга взаимно.", "Бири-бириңерди чакырууга болбойт.");
        }
        user.setReferredBy(inviter);
        userRepository.save(user);
        log.info("Referral applied: userId={}, inviterId={}", userId, inviter.getId());
        return getInfo(userId, lang);
    }

    /** Called when a payment completes: the invitee's first purchase earns the inviter a reward. */
    @Transactional
    public void onPaymentCompleted(Payment payment) {
        User invitee = payment.getUser();
        User inviter = invitee.getReferredBy();
        if (inviter == null || rewardRepository.existsByInviteeId(invitee.getId())) return;

        rewardRepository.save(ReferralReward.builder()
                .inviter(inviter)
                .invitee(invitee)
                .payment(payment)
                .build());
        log.info("Referral reward earned: inviterId={}, inviteeId={}, paymentId={}",
                inviter.getId(), invitee.getId(), payment.getPaymentId());
        try {
            pushNotificationService.notifyUser(inviter.getId(), PushCategory.MARKETING,
                    PushMessages.referralRewardEarned(invitee.getFirstName()),
                    PushDataType.NONE, null);
        } catch (Exception e) {
            log.warn("referral reward notification failed: {}", e.getMessage());
        }
    }

    /** Spend one available reward to unlock a test permanently. */
    @Transactional
    public ReferralInfoResponse redeem(Long userId, Long testId, String lang) {
        List<ReferralReward> available = rewardRepository.findByInviterIdAndRedeemedAtIsNullOrderByCreatedAtAsc(userId);
        if (available.isEmpty()) {
            throw new AppException("Нет доступных наград за друзей.", "Достор үчүн сыйлык жок.");
        }
        Test test = testRepository.findById(testId)
                .filter(Test::getActive)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));
        LocalDateTime now = LocalDateTime.now();
        List<SubTest> sections = subTestRepository.findByTestIdAndActiveTrueOrderByLevelOrderAsc(testId);
        if (accessResolver.hasTestAccess(userId, test, now)) {
            throw new AppException("Этот тест уже открыт — выберите другой.", "Бул тест мурунтан ачык — башкасын тандаңыз.");
        }

        User user = findUser(userId);
        UserTestAccess access = userTestAccessRepository.findByUserIdAndTestId(userId, testId)
                .orElseGet(() -> UserTestAccess.builder().user(user).test(test).build());
        access.setGrantedAt(now);
        access.setExpiresAt(null);
        userTestAccessRepository.save(access);

        ReferralReward reward = available.get(0);
        reward.setRedeemedTest(test);
        reward.setRedeemedAt(now);
        rewardRepository.save(reward);
        log.info("Referral reward redeemed: userId={}, rewardId={}, testId={}", userId, reward.getId(), testId);
        return getInfo(userId, lang);
    }

    // ===== helpers =====

    private boolean canApplyCode(User user) {
        return user.getReferredBy() == null
                && paymentRepository.countByUserIdAndStatus(user.getId(), Payment.PaymentStatus.COMPLETED) == 0;
    }

    private String ensureCode(User user) {
        if (user.getReferralCode() != null) return user.getReferralCode();
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            code = sb.toString();
        } while (userRepository.existsByReferralCode(code));
        user.setReferralCode(code);
        userRepository.save(user);
        return code;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException("Пользователь не найден.", "Колдонуучу табылган жок."));
    }

    private static String displayName(User u) {
        if (u.getFirstName() == null && u.getLastName() == null) return null;
        return ((u.getFirstName() != null ? u.getFirstName() : "") + " "
                + (u.getLastName() != null ? u.getLastName().charAt(0) + "." : "")).trim();
    }
}
