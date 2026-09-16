package synamyk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import synamyk.entities.SubTest;
import synamyk.entities.Test;
import synamyk.enums.ProductCode;
import synamyk.repo.UserAllAccessRepository;
import synamyk.repo.UserSubTestAccessRepository;
import synamyk.repo.UserTestAccessRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Single source of truth for "can this user open this sub-test / test right now?".
 *
 * <p>Order of precedence for a sub-test:
 * <ol>
 *   <li>sub-test is not paid → open</li>
 *   <li>parent test is inside its free window → open for everyone</li>
 *   <li>sub-test is inside its free window → open for everyone</li>
 *   <li>user bought «все тесты» → open</li>
 *   <li>user has an active whole-test grant (purchase / referral reward / manual grant) → open</li>
 *   <li>user has an active sub-test grant (independent purchase / manual grant) → open</li>
 *   <li>otherwise → closed</li>
 * </ol>
 * A whole test is open when every one of its active sections is open.
 */
@Component
@RequiredArgsConstructor
public class AccessResolver {

    private final UserTestAccessRepository userTestAccessRepo;
    private final UserSubTestAccessRepository userSubTestAccessRepo;
    private final UserAllAccessRepository userAllAccessRepo;

    /**
     * A free window is active only if at least one bound is set and {@code now}
     * falls inside it. Both bounds {@code null} means "no window" (normal paid
     * behaviour), not "free forever".
     */
    public static boolean isFreeNow(LocalDateTime freeFrom, LocalDateTime freeUntil, LocalDateTime now) {
        if (freeFrom == null && freeUntil == null) return false;
        boolean afterStart = (freeFrom == null) || !now.isBefore(freeFrom);
        boolean beforeEnd = (freeUntil == null) || now.isBefore(freeUntil);
        return afterStart && beforeEnd;
    }

    /** True if either the parent test or the sub-test is currently in a free window. */
    public boolean isEffectivelyFree(SubTest st, LocalDateTime now) {
        Test t = st.getTest();
        return isFreeNow(t.getFreeFrom(), t.getFreeUntil(), now)
                || isFreeNow(st.getFreeFrom(), st.getFreeUntil(), now);
    }

    /**
     * Nearest date on which the current free window ends, or {@code null} if the
     * content is not currently free or the active window is open-ended.
     * Used by the mobile client for a "free for N more days" badge.
     */
    public LocalDateTime freeUntilBoundary(SubTest st, LocalDateTime now) {
        Test t = st.getTest();
        LocalDateTime result = null;
        if (isFreeNow(t.getFreeFrom(), t.getFreeUntil(), now) && t.getFreeUntil() != null) {
            result = t.getFreeUntil();
        }
        if (isFreeNow(st.getFreeFrom(), st.getFreeUntil(), now) && st.getFreeUntil() != null) {
            if (result == null || st.getFreeUntil().isBefore(result)) result = st.getFreeUntil();
        }
        return result;
    }

    public boolean hasSubTestAccess(Long userId, SubTest st, LocalDateTime now) {
        if (!Boolean.TRUE.equals(st.getIsPaid())) return true;
        if (isEffectivelyFree(st, now)) return true;
        if (userAllAccessRepo.existsActiveAccess(userId, ProductCode.ALL_TESTS, now)) return true;
        Long testId = st.getTest().getId();
        if (userTestAccessRepo.existsActiveAccess(userId, testId, now)) return true;
        if (userSubTestAccessRepo.existsActiveAccess(userId, st.getId(), now)) return true;
        return false;
    }

    /** «Акысыз» badge: no active section requires payment, or the test is in a free window. */
    public static boolean isTestFree(Test test, List<SubTest> activeSections, LocalDateTime now) {
        if (isFreeNow(test.getFreeFrom(), test.getFreeUntil(), now)) return true;
        return activeSections.stream().noneMatch(st -> Boolean.TRUE.equals(st.getIsPaid())
                && !isFreeNow(st.getFreeFrom(), st.getFreeUntil(), now));
    }

    /** The whole test can be started: every active section is open for this user. */
    public boolean hasTestAccess(Long userId, Test test, List<SubTest> activeSections, LocalDateTime now) {
        if (isTestFree(test, activeSections, now)) return true;
        if (userAllAccessRepo.existsActiveAccess(userId, ProductCode.ALL_TESTS, now)) return true;
        if (userTestAccessRepo.existsActiveAccess(userId, test.getId(), now)) return true;
        return activeSections.stream().allMatch(st -> hasSubTestAccess(userId, st, now));
    }

    public boolean hasAllAccess(Long userId, ProductCode product, LocalDateTime now) {
        return userAllAccessRepo.existsActiveAccess(userId, product, now);
    }
}
