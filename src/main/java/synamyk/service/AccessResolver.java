package synamyk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import synamyk.entities.SubTest;
import synamyk.entities.Test;
import synamyk.enums.ProductCode;
import synamyk.repo.UserAllAccessRepository;
import synamyk.repo.UserTestAccessRepository;

import java.time.LocalDateTime;

/**
 * Single source of truth for "can this user open this test right now?".
 *
 * <p>Payment happens for a whole test only — sections are never sold separately.
 * Order of precedence:
 * <ol>
 *   <li>test costs nothing (price is 0 or unset) → open</li>
 *   <li>test is inside its free window → open for everyone</li>
 *   <li>user bought «все тесты» → open</li>
 *   <li>user has an active test grant (purchase / referral reward / manual grant) → open</li>
 *   <li>otherwise → closed</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class AccessResolver {

    private final UserTestAccessRepository userTestAccessRepo;
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

    /** «Акысыз» badge: the test costs nothing, or its free window is open right now. */
    public static boolean isTestFree(Test test, LocalDateTime now) {
        if (test.getPrice() == null || test.getPrice().signum() <= 0) return true;
        return isFreeNow(test.getFreeFrom(), test.getFreeUntil(), now);
    }

    /**
     * End of the test's active free window, or {@code null} if the test is not
     * currently inside a window or the window is open-ended.
     * Used by the mobile client for a "free for N more days" badge.
     */
    public static LocalDateTime freeUntilBoundary(Test test, LocalDateTime now) {
        return isFreeNow(test.getFreeFrom(), test.getFreeUntil(), now) ? test.getFreeUntil() : null;
    }

    /** The test — and therefore every section in it — can be opened by this user. */
    public boolean hasTestAccess(Long userId, Test test, LocalDateTime now) {
        if (isTestFree(test, now)) return true;
        if (userId == null) return false;
        if (userAllAccessRepo.existsActiveAccess(userId, ProductCode.ALL_TESTS, now)) return true;
        return userTestAccessRepo.existsActiveAccess(userId, test.getId(), now);
    }

    /** Sections are not sold separately: access to a section is access to its test. */
    public boolean hasSubTestAccess(Long userId, SubTest subTest, LocalDateTime now) {
        return hasTestAccess(userId, subTest.getTest(), now);
    }

    public boolean hasAllAccess(Long userId, ProductCode product, LocalDateTime now) {
        return userAllAccessRepo.existsActiveAccess(userId, product, now);
    }
}
