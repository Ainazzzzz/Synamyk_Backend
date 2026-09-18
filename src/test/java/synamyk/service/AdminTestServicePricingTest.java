package synamyk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import synamyk.dto.admin.ScheduleRequest;
import synamyk.dto.admin.UpdateTestPricingRequest;
import synamyk.exception.AppException;
import synamyk.repo.AnswerOptionRepository;
import synamyk.repo.QuestionRepository;
import synamyk.repo.SubTestRepository;
import synamyk.repo.TestRepository;
import synamyk.repo.UserTestAccessRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTestServicePricingTest {

    @Mock TestRepository testRepository;
    @Mock SubTestRepository subTestRepository;
    @Mock QuestionRepository questionRepository;
    @Mock AnswerOptionRepository optionRepository;
    @Mock MinioService minioService;
    @Mock UserTestAccessRepository userTestAccessRepository;
    @Mock PushNotificationService pushNotificationService;

    @InjectMocks AdminTestService service;

    private synamyk.entities.Test test() {
        synamyk.entities.Test t = new synamyk.entities.Test();
        t.setId(1L);
        t.setTitle("ОРТ");
        t.setPrice(BigDecimal.ZERO);
        return t;
    }

    @org.junit.jupiter.api.BeforeEach
    void stubReadBack() {
        lenient().when(questionRepository.countBySubTestIdAndActiveTrue(anyLong())).thenReturn(0L);
        lenient().when(minioService.presign(any())).thenReturn(null);
        lenient().when(subTestRepository.findByTestIdOrderByLevelOrderAsc(1L)).thenReturn(List.of());
    }

    @Test
    void updateTestPricing_setsTestPrice() {
        synamyk.entities.Test t = test();
        when(testRepository.findById(1L)).thenReturn(Optional.of(t));

        UpdateTestPricingRequest req = new UpdateTestPricingRequest();
        req.setPrice(new BigDecimal("1000"));

        service.updateTestPricing(1L, req);

        assertThat(t.getPrice()).isEqualByComparingTo("1000");
    }

    @Test
    void updateTestPricing_zeroMakesTestFree() {
        synamyk.entities.Test t = test();
        t.setPrice(new BigDecimal("700"));
        when(testRepository.findById(1L)).thenReturn(Optional.of(t));

        UpdateTestPricingRequest req = new UpdateTestPricingRequest();
        req.setPrice(BigDecimal.ZERO);

        service.updateTestPricing(1L, req);

        assertThat(t.getPrice()).isEqualByComparingTo("0");
        assertThat(AccessResolver.isTestFree(t, LocalDateTime.now())).isTrue();
    }

    @Test
    void updateTestPricing_negativePrice_rejected() {
        when(testRepository.findById(1L)).thenReturn(Optional.of(test()));

        UpdateTestPricingRequest req = new UpdateTestPricingRequest();
        req.setPrice(new BigDecimal("-1"));

        assertThatThrownBy(() -> service.updateTestPricing(1L, req))
                .isInstanceOf(AppException.class);
    }

    @Test
    void updateTestSchedule_endBeforeStart_rejected() {
        when(testRepository.findById(1L)).thenReturn(Optional.of(test()));
        ScheduleRequest req = new ScheduleRequest();
        req.setFreeFrom(LocalDateTime.of(2026, 9, 8, 0, 0));
        req.setFreeUntil(LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThatThrownBy(() -> service.updateTestSchedule(1L, req))
                .isInstanceOf(AppException.class);
    }

    @Test
    void updateTestSchedule_clearsWindow() {
        synamyk.entities.Test t = test();
        t.setFreeFrom(LocalDateTime.now());
        t.setFreeUntil(LocalDateTime.now().plusDays(1));
        when(testRepository.findById(1L)).thenReturn(Optional.of(t));

        ScheduleRequest req = new ScheduleRequest(); // both null

        service.updateTestSchedule(1L, req);

        assertThat(t.getFreeFrom()).isNull();
        assertThat(t.getFreeUntil()).isNull();
    }
}
