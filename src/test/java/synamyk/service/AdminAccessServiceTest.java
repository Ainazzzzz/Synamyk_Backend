package synamyk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import synamyk.dto.admin.AccessGrantResponse;
import synamyk.dto.admin.GrantAccessRequest;
import synamyk.entities.User;
import synamyk.entities.UserTestAccess;
import synamyk.exception.AppException;
import synamyk.repo.TestRepository;
import synamyk.repo.UserRepository;
import synamyk.repo.UserTestAccessRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccessServiceTest {

    @Mock UserTestAccessRepository accessRepository;
    @Mock UserRepository userRepository;
    @Mock TestRepository testRepository;

    @InjectMocks AdminAccessService service;

    private User user() {
        User u = new User();
        u.setId(5L);
        u.setFirstName("Aida");
        u.setPhone("+996700");
        return u;
    }

    private synamyk.entities.Test test() {
        synamyk.entities.Test t = new synamyk.entities.Test();
        t.setId(2L);
        t.setTitle("ОРТ");
        return t;
    }

    @Test
    void grant_byTestId_upsertsTestAccess() {
        GrantAccessRequest r = new GrantAccessRequest();
        r.setUserId(5L);
        r.setTestId(2L);
        r.setDurationDays(30);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user()));
        when(testRepository.findById(2L)).thenReturn(Optional.of(test()));
        when(accessRepository.findByUserIdAndTestId(5L, 2L)).thenReturn(Optional.empty());
        when(accessRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AccessGrantResponse res = service.grant(r);

        ArgumentCaptor<UserTestAccess> captor = ArgumentCaptor.forClass(UserTestAccess.class);
        verify(accessRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(res.testId()).isEqualTo(2L);
        assertThat(res.testTitle()).isEqualTo("ОРТ");
        assertThat(res.status()).isEqualTo("ACTIVE");
    }

    @Test
    void grant_permanentWhenNoTerm() {
        GrantAccessRequest r = new GrantAccessRequest();
        r.setUserId(5L);
        r.setTestId(2L);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user()));
        when(testRepository.findById(2L)).thenReturn(Optional.of(test()));
        when(accessRepository.findByUserIdAndTestId(5L, 2L)).thenReturn(Optional.empty());
        when(accessRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AccessGrantResponse res = service.grant(r);

        assertThat(res.expiresAt()).isNull();
        assertThat(res.status()).isEqualTo("PERMANENT");
    }

    @Test
    void grant_noTestId_rejected() {
        GrantAccessRequest r = new GrantAccessRequest();
        r.setUserId(5L);

        assertThatThrownBy(() -> service.grant(r)).isInstanceOf(AppException.class);
    }

    @Test
    void revoke_delegatesToRepo() {
        service.revoke(5L, 2L);
        verify(accessRepository).deleteByUserIdAndTestId(5L, 2L);
    }

    @Test
    void listByUser_returnsTestGrants() {
        UserTestAccess ta = UserTestAccess.builder()
                .user(user()).test(test())
                .grantedAt(LocalDateTime.now().minusDays(2))
                .build();
        ta.setId(11L);

        when(accessRepository.findByUserIdOrderByGrantedAtDesc(5L)).thenReturn(List.of(ta));

        List<AccessGrantResponse> list = service.listByUser(5L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(11L);
        assertThat(list.get(0).testId()).isEqualTo(2L);
    }
}
