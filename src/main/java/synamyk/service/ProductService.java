package synamyk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import synamyk.dto.product.AppConfigResponse;
import synamyk.dto.product.ProductResponse;
import synamyk.dto.product.UpdateProductRequest;
import synamyk.entities.AppSetting;
import synamyk.entities.ProductPrice;
import synamyk.enums.ProductCode;
import synamyk.exception.AppException;
import synamyk.repo.AppSettingRepository;
import synamyk.repo.ProductPriceRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductPriceRepository productPriceRepository;
    private final AppSettingRepository appSettingRepository;
    private final AccessResolver accessResolver;

    public List<ProductResponse> list(Long userId, String lang) {
        LocalDateTime now = LocalDateTime.now();
        return Arrays.stream(ProductCode.values())
                .map(code -> toResponse(getOrDefault(code), userId != null && accessResolver.hasAllAccess(userId, code, now), lang))
                .toList();
    }

    public ProductResponse get(ProductCode code, Long userId, String lang) {
        return toResponse(getOrDefault(code), accessResolver.hasAllAccess(userId, code, LocalDateTime.now()), lang);
    }

    /** Active product with a positive price, or a 400. */
    public ProductPrice requireSellable(ProductCode code) {
        ProductPrice p = getOrDefault(code);
        if (!Boolean.TRUE.equals(p.getActive()) || p.getPrice() == null || p.getPrice().signum() <= 0) {
            throw new AppException("Этот продукт сейчас не продаётся.", "Бул продукт азыр сатылбайт.");
        }
        return p;
    }

    @Transactional
    public ProductResponse update(ProductCode code, UpdateProductRequest request) {
        ProductPrice p = productPriceRepository.findByCode(code)
                .orElseGet(() -> ProductPrice.builder().code(code).build());
        if (Boolean.TRUE.equals(request.getActive()) && request.getPrice().signum() <= 0) {
            throw new AppException("Для продажи цена должна быть больше 0.", "Сатуу үчүн баа 0дөн жогору болушу керек.");
        }
        p.setPrice(request.getPrice());
        p.setOldPrice(request.getOldPrice());
        p.setActive(request.getActive());
        productPriceRepository.save(p);
        log.info("Product updated: code={}, price={}, active={}", code, p.getPrice(), p.getActive());
        return toResponse(p, false, "RU");
    }

    // ===== app config =====

    public AppConfigResponse config() {
        LocalDateTime exam = examDate();
        Long seconds = exam == null ? null : Math.max(0, Duration.between(LocalDateTime.now(), exam).getSeconds());
        return AppConfigResponse.builder()
                .ortExamDate(exam)
                .secondsUntilExam(seconds)
                .ortMaxScore(245)
                .ortThresholdScore(TestAttemptService.ORT_THRESHOLD)
                .schoolRatingMinStudents(SchoolService.MIN_STUDENTS_FOR_RATING)
                .build();
    }

    @Transactional
    public AppConfigResponse setExamDate(LocalDateTime date) {
        AppSetting s = appSettingRepository.findById(AppSetting.ORT_EXAM_DATE)
                .orElseGet(() -> AppSetting.builder().key(AppSetting.ORT_EXAM_DATE).build());
        s.setValue(date != null ? date.toString() : null);
        appSettingRepository.save(s);
        return config();
    }

    private LocalDateTime examDate() {
        return appSettingRepository.findById(AppSetting.ORT_EXAM_DATE)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .map(v -> {
                    try {
                        return LocalDateTime.parse(v);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    // ===== helpers =====

    private ProductPrice getOrDefault(ProductCode code) {
        return productPriceRepository.findByCode(code)
                .orElseGet(() -> ProductPrice.builder().code(code).build());
    }

    public static String title(ProductCode code, String lang) {
        boolean ky = "KY".equalsIgnoreCase(lang);
        return switch (code) {
            case ALL_TESTS -> ky ? "Бардык тесттерди ачуу" : "Открыть все тесты";
            case ALL_TEXTS -> ky ? "Окуу тексттерин ачуу" : "Открыть тексты для чтения";
        };
    }

    private ProductResponse toResponse(ProductPrice p, boolean owned, String lang) {
        boolean ky = "KY".equalsIgnoreCase(lang);
        List<String> features;
        String description;
        if (p.getCode() == ProductCode.ALL_TESTS) {
            description = ky ? "Бир жолку төлөм менен бардык тесттерди ачыңыз" : "Одна оплата открывает все тесты";
            features = ky
                    ? List.of("Бардык негизги тесттер ачылат", "Жаңы тесттер да кошулат", "Чектөөсүз колдонуу — бир жолку төлөм")
                    : List.of("Открываются все основные тесты", "Новые тесты — тоже включены", "Без ограничений — разовая оплата");
        } else {
            description = ky ? "Бир жолку төлөм менен бардык окуу түшүнүү тексттери ачылат"
                    : "Одна оплата открывает все тексты для чтения";
            features = ky
                    ? List.of("Бардык окуу түшүнүү тексттери ачылат", "PDF форматында, каалаган убакта оку", "Чектөөсүз колдонуу — бир жолку төлөм")
                    : List.of("Открываются все тексты на понимание", "PDF — читайте в любое время", "Без ограничений — разовая оплата");
        }
        return ProductResponse.builder()
                .code(p.getCode().name())
                .title(title(p.getCode(), lang))
                .description(description)
                .price(p.getPrice())
                .oldPrice(p.getOldPrice())
                .available(Boolean.TRUE.equals(p.getActive()) && p.getPrice() != null && p.getPrice().signum() > 0)
                .owned(owned)
                .features(features)
                .build();
    }
}
