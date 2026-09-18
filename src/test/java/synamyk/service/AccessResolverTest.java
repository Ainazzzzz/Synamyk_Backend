package synamyk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import synamyk.entities.SubTest;
import synamyk.enums.ProductCode;
import synamyk.repo.UserAllAccessRepository;
import synamyk.repo.UserTestAccessRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessResolverTest {

    @Mock UserTestAccessRepository userTestAccessRepo;
    @Mock UserAllAccessRepository userAllAccessRepo;

    @InjectMocks AccessResolver resolver;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 12, 0);

    // ===== isFreeNow =====

    @Test
    void isFreeNow_noBounds_isNotFree() {
        assertThat(AccessResolver.isFreeNow(null, null, NOW)).isFalse();
    }

    @Test
    void isFreeNow_onlyUntil_freeBeforeIt() {
        assertThat(AccessResolver.isFreeNow(null, NOW.plusDays(3), NOW)).isTrue();
        assertThat(AccessResolver.isFreeNow(null, NOW.minusDays(1), NOW)).isFalse();
    }

    @Test
    void isFreeNow_onlyFrom_freeFromItOnward() {
        assertThat(AccessResolver.isFreeNow(NOW.minusDays(1), null, NOW)).isTrue();
        assertThat(AccessResolver.isFreeNow(NOW.plusDays(1), null, NOW)).isFalse();
    }

    @Test
    void isFreeNow_range_freeOnlyInside() {
        assertThat(AccessResolver.isFreeNow(NOW.minusDays(1), NOW.plusDays(1), NOW)).isTrue();
    }

    @Test
    void isFreeNow_range_outside_isNotFree() {
        assertThat(AccessResolver.isFreeNow(NOW.plusDays(1), NOW.plusDays(2), NOW)).isFalse();
        assertThat(AccessResolver.isFreeNow(NOW.minusDays(2), NOW.minusDays(1), NOW)).isFalse();
    }

    @Test
    void isFreeNow_untilBoundaryIsExclusive() {
        assertThat(AccessResolver.isFreeNow(null, NOW, NOW)).isFalse();
    }

    // ===== доступ к тесту и его разделам =====

    private synamyk.entities.Test test(String price) {
        synamyk.entities.Test t = new synamyk.entities.Test();
        t.setId(1L);
        t.setPrice(new BigDecimal(price));
        return t;
    }

    private SubTest sectionOf(synamyk.entities.Test t) {
        SubTest st = new SubTest();
        st.setId(10L);
        st.setTest(t);
        return st;
    }

    @Test
    void freeTest_alwaysAccessible() {
        synamyk.entities.Test t = test("0");
        assertThat(AccessResolver.isTestFree(t, NOW)).isTrue();
        assertThat(resolver.hasTestAccess(7L, t, NOW)).isTrue();
        assertThat(resolver.hasSubTestAccess(7L, sectionOf(t), NOW)).isTrue();
    }

    @Test
    void paidTest_freeWindowOpen_accessible() {
        synamyk.entities.Test t = test("500");
        t.setFreeUntil(NOW.plusDays(2));
        assertThat(resolver.hasSubTestAccess(7L, sectionOf(t), NOW)).isTrue();
        assertThat(AccessResolver.freeUntilBoundary(t, NOW)).isEqualTo(NOW.plusDays(2));
    }

    @Test
    void paidTest_activeGrant_accessible() {
        synamyk.entities.Test t = test("500");
        when(userAllAccessRepo.existsActiveAccess(7L, ProductCode.ALL_TESTS, NOW)).thenReturn(false);
        when(userTestAccessRepo.existsActiveAccess(7L, 1L, NOW)).thenReturn(true);
        assertThat(resolver.hasSubTestAccess(7L, sectionOf(t), NOW)).isTrue();
    }

    @Test
    void paidTest_allTestsProduct_accessible() {
        synamyk.entities.Test t = test("500");
        when(userAllAccessRepo.existsActiveAccess(7L, ProductCode.ALL_TESTS, NOW)).thenReturn(true);
        assertThat(resolver.hasTestAccess(7L, t, NOW)).isTrue();
    }

    @Test
    void paidTest_noGrantNoWindow_notAccessible() {
        synamyk.entities.Test t = test("500");
        when(userAllAccessRepo.existsActiveAccess(7L, ProductCode.ALL_TESTS, NOW)).thenReturn(false);
        when(userTestAccessRepo.existsActiveAccess(7L, 1L, NOW)).thenReturn(false);
        assertThat(resolver.hasSubTestAccess(7L, sectionOf(t), NOW)).isFalse();
    }

    @Test
    void paidTest_expiredWindow_notAccessible() {
        synamyk.entities.Test t = test("500");
        t.setFreeUntil(NOW.minusDays(1));
        lenient().when(userAllAccessRepo.existsActiveAccess(7L, ProductCode.ALL_TESTS, NOW)).thenReturn(false);
        lenient().when(userTestAccessRepo.existsActiveAccess(7L, 1L, NOW)).thenReturn(false);
        assertThat(resolver.hasTestAccess(7L, t, NOW)).isFalse();
        assertThat(AccessResolver.freeUntilBoundary(t, NOW)).isNull();
    }

    @Test
    void anonymousUser_paidTest_notAccessible() {
        assertThat(resolver.hasTestAccess(null, test("500"), NOW)).isFalse();
    }
}
