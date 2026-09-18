package synamyk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import synamyk.entities.*;
import synamyk.exception.AppException;
import synamyk.repo.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock UserRepository userRepository;
    @Mock ReferralRewardRepository rewardRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock TestRepository testRepository;
    @Mock SubTestRepository subTestRepository;
    @Mock UserTestAccessRepository userTestAccessRepository;
    @Mock AccessResolver accessResolver;
    @Mock PushNotificationService pushNotificationService;

    @InjectMocks ReferralService service;

    private static User user(long id) {
        return User.builder().id(id).phone("99655500000" + id).password("x").firstName("U" + id).build();
    }

    @Test
    void onPaymentCompleted_firstPurchaseOfInvitedFriend_createsReward() {
        User inviter = user(1);
        User friend = user(2);
        friend.setReferredBy(inviter);
        Payment payment = Payment.builder().user(friend).build();
        when(rewardRepository.existsByInviteeId(2L)).thenReturn(false);

        service.onPaymentCompleted(payment);

        ArgumentCaptor<ReferralReward> captor = ArgumentCaptor.forClass(ReferralReward.class);
        verify(rewardRepository).save(captor.capture());
        assertThat(captor.getValue().getInviter()).isSameAs(inviter);
        assertThat(captor.getValue().getInvitee()).isSameAs(friend);
        assertThat(captor.getValue().isAvailable()).isTrue();
    }

    @Test
    void onPaymentCompleted_secondPurchase_noSecondReward() {
        User friend = user(2);
        friend.setReferredBy(user(1));
        when(rewardRepository.existsByInviteeId(2L)).thenReturn(true);

        service.onPaymentCompleted(Payment.builder().user(friend).build());

        verify(rewardRepository, never()).save(any());
    }

    @Test
    void onPaymentCompleted_notInvited_noReward() {
        service.onPaymentCompleted(Payment.builder().user(user(2)).build());
        verify(rewardRepository, never()).save(any());
    }

    @Test
    void apply_ownCode_rejected() {
        User me = user(1);
        me.setReferralCode("ABC123");
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findByReferralCode("ABC123")).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.apply(1L, "abc123", "RU")).isInstanceOf(AppException.class);
    }

    @Test
    void apply_afterPurchase_rejected() {
        User me = user(2);
        when(userRepository.findById(2L)).thenReturn(Optional.of(me));
        when(userRepository.findByReferralCode("ABC123")).thenReturn(Optional.of(user(1)));
        when(paymentRepository.countByUserIdAndStatus(2L, Payment.PaymentStatus.COMPLETED)).thenReturn(1L);

        assertThatThrownBy(() -> service.apply(2L, "ABC123", "RU")).isInstanceOf(AppException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void redeem_grantsPermanentTestAccess_andMarksRewardUsed() {
        User me = user(1);
        me.setReferralCode("ABC123");
        synamyk.entities.Test test = synamyk.entities.Test.builder().id(9L).title("T").active(true).build();
        ReferralReward reward = ReferralReward.builder().id(5L).inviter(me).invitee(user(2)).build();

        when(rewardRepository.findByInviterIdAndRedeemedAtIsNullOrderByCreatedAtAsc(1L)).thenReturn(List.of(reward));
        when(testRepository.findById(9L)).thenReturn(Optional.of(test));
        when(subTestRepository.findByTestIdAndActiveTrueOrderByLevelOrderAsc(9L)).thenReturn(List.of());
        when(accessResolver.hasTestAccess(eq(1L), eq(test), any())).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userTestAccessRepository.findByUserIdAndTestId(1L, 9L)).thenReturn(Optional.empty());
        when(rewardRepository.findByInviterIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of(reward));

        service.redeem(1L, 9L, "RU");

        ArgumentCaptor<UserTestAccess> access = ArgumentCaptor.forClass(UserTestAccess.class);
        verify(userTestAccessRepository).save(access.capture());
        assertThat(access.getValue().getExpiresAt()).isNull();
        assertThat(reward.getRedeemedTest()).isSameAs(test);
        assertThat(reward.isAvailable()).isFalse();
    }

    @Test
    void redeem_testAlreadyOpen_rejected() {
        synamyk.entities.Test test = synamyk.entities.Test.builder().id(9L).title("T").active(true).build();
        when(rewardRepository.findByInviterIdAndRedeemedAtIsNullOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(ReferralReward.builder().build()));
        when(testRepository.findById(9L)).thenReturn(Optional.of(test));
        when(accessResolver.hasTestAccess(eq(1L), eq(test), any())).thenReturn(true);

        assertThatThrownBy(() -> service.redeem(1L, 9L, "RU")).isInstanceOf(AppException.class);
        verify(userTestAccessRepository, never()).save(any());
    }
}
