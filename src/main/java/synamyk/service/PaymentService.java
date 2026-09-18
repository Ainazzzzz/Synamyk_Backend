package synamyk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import synamyk.dto.CreatePaymentResponse;
import synamyk.dto.InitPaymentResponse;
import synamyk.dto.PaymentHistoryEntry;
import synamyk.dto.WebhookData;
import synamyk.util.L10n;
import synamyk.entities.Payment;
import synamyk.entities.ProductPrice;
import synamyk.entities.UserAllAccess;
import synamyk.enums.PaymentProduct;
import synamyk.enums.ProductCode;
import synamyk.repo.UserAllAccessRepository;
import synamyk.entities.Test;
import synamyk.entities.User;
import synamyk.entities.UserTestAccess;
import synamyk.config.FinikConfig;
import synamyk.exception.AppException;
import synamyk.repo.PaymentRepository;
import synamyk.repo.TestRepository;
import synamyk.repo.UserRepository;
import synamyk.repo.UserTestAccessRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final TestRepository testRepository;
    private final UserTestAccessRepository accessRepository;
    private final AccessResolver accessResolver;
    private final FinikConfig finikConfig;
    private final UserAllAccessRepository allAccessRepository;
    private final ProductService productService;
    private final ReferralService referralService;

    /**
     * Step 1 for Flutter SDK: create a Payment record in DB and return config
     * for the Flutter finik_sdk (CreateItemHandlerWidget).
     *
     * Flutter should:
     *   - pass paymentId as `requestId`
     *   - pass paymentId in `requiredFields` as a hidden field so it comes back in webhook fields
     *   - pass callbackUrl as `callbackUrl`
     */
    /** Current user's payment history, newest first. PENDING (abandoned) attempts are hidden. */
    @Transactional(readOnly = true)
    public Page<PaymentHistoryEntry> getMyPayments(Long userId, String lang, Pageable pageable) {
        return paymentRepository.findMyPayments(userId, Payment.PaymentStatus.PENDING, pageable)
                .map(p -> new PaymentHistoryEntry(
                        p.getPaymentId(),
                        p.resolveProduct().name(),
                        p.getTest() != null ? p.getTest().getId() : null,
                        p.getTest() != null
                                ? L10n.pick(p.getTest().getTitle(), p.getTest().getTitleKy(), lang)
                                : productTitle(p.resolveProduct(), lang),
                        p.getAmount(),
                        p.getStatus().name(),
                        p.getReceiptNumber(),
                        p.getPaymentUrl(),
                        p.getCreatedAt(),
                        p.getPaidAt()));
    }

    /** Buy access to the whole test (bundle) — unchanged behaviour. */
    @Transactional
    public InitPaymentResponse initPayment(Long userId, Long testId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new AppException("Тест не найден.", "Тест табылган жок."));

        LocalDateTime now = LocalDateTime.now();
        if (accessRepository.existsActiveAccess(userId, testId, now)
                || accessResolver.hasAllAccess(userId, ProductCode.ALL_TESTS, now)) {
            throw new AppException("Уже куплено.", "Мурунтан эле сатып алынган.");
        }
        if (test.getPrice() == null || test.getPrice().signum() <= 0) {
            throw new AppException("Этот тест не продаётся.", "Бул тест сатылбайт.");
        }

        UUID paymentId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .user(user)
                .test(test)
                .product(PaymentProduct.TEST)
                .paymentId(paymentId)
                .amount(test.getPrice())
                .status(Payment.PaymentStatus.PENDING)
                .build();

        paymentRepository.save(payment);
        log.info("Payment record created (bundle): paymentId={}, userId={}, testId={}", paymentId, userId, testId);

        return InitPaymentResponse.builder()
                .paymentId(paymentId)
                .amount(test.getPrice())
                .nameEn(truncate(test.getTitle(), 50))
                .callbackUrl(finikConfig.getWebhookUrl())
                .build();
    }

    /** «Купить все тесты» / «Открыть все тексты». */
    @Transactional
    public InitPaymentResponse initPaymentProduct(Long userId, ProductCode code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("Пользователь не найден.", "Колдонуучу табылган жок."));
        if (accessResolver.hasAllAccess(userId, code, LocalDateTime.now())) {
            throw new AppException("Уже куплено.", "Мурунтан эле сатып алынган.");
        }
        ProductPrice product = productService.requireSellable(code);

        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(Payment.builder()
                .user(user)
                .product(code.toPaymentProduct())
                .paymentId(paymentId)
                .amount(product.getPrice())
                .status(Payment.PaymentStatus.PENDING)
                .build());
        log.info("Payment record created (product): paymentId={}, userId={}, product={}", paymentId, userId, code);

        return InitPaymentResponse.builder()
                .paymentId(paymentId)
                .amount(product.getPrice())
                .nameEn(code == ProductCode.ALL_TESTS ? "Synamyk - all tests" : "Synamyk - all reading texts")
                .callbackUrl(finikConfig.getWebhookUrl())
                .build();
    }

    private static String productTitle(PaymentProduct product, String lang) {
        return switch (product) {
            case ALL_TESTS -> ProductService.title(ProductCode.ALL_TESTS, lang);
            case ALL_TEXTS -> ProductService.title(ProductCode.ALL_TEXTS, lang);
            default -> null;
        };
    }

    private String truncate(String str, int max) {
        return str != null && str.length() > max ? str.substring(0, max) : str;
    }

    @Transactional
    public void processWebhook(WebhookData webhookData, String rawJson) {
        String transactionId = webhookData.getTransactionId();
        log.info("Processing webhook: transactionId={}, status={}", transactionId, webhookData.getStatus());

        if (paymentRepository.findByTransactionId(transactionId).isPresent()) {
            log.info("Webhook already processed: {}", transactionId);
            return;
        }

        if (!"SUCCEEDED".equals(webhookData.getStatus())) {
            log.warn("Unexpected webhook status: {}", webhookData.getStatus());
            return;
        }

        Payment payment = findPaymentForWebhook(webhookData);
        if (payment == null) {
            log.error("Payment not found for webhook: transactionId={}", transactionId);
            return;
        }

        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
            log.warn("Payment already completed: paymentId={}", payment.getPaymentId());
            return;
        }

        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionId(transactionId);
        payment.setReceiptNumber(webhookData.getReceiptNumber());
        payment.setWebhookData(rawJson);
        paymentRepository.save(payment);

        grantAccess(payment);
        referralService.onPaymentCompleted(payment);

        log.info("Payment completed: paymentId={}, userId={}, product={}, testId={}",
                payment.getPaymentId(), payment.getUser().getId(), payment.resolveProduct(),
                payment.getTest() != null ? payment.getTest().getId() : null);
    }

    /** Route a completed payment to the right access grant. */
    private void grantAccess(Payment payment) {
        PaymentProduct product = payment.resolveProduct();
        if (product == PaymentProduct.ALL_TESTS || product == PaymentProduct.ALL_TEXTS) {
            ProductCode code = product == PaymentProduct.ALL_TESTS ? ProductCode.ALL_TESTS : ProductCode.ALL_TEXTS;
            User user = payment.getUser();
            UserAllAccess access = allAccessRepository.findByUserIdAndProduct(user.getId(), code)
                    .orElseGet(() -> UserAllAccess.builder().user(user).product(code).build());
            access.setGrantedAt(LocalDateTime.now());
            access.setExpiresAt(null); // a purchase grants permanent access
            allAccessRepository.save(access);
            log.info("All-access granted (permanent): userId={}, product={}", user.getId(), code);
        } else {
            grantTestAccess(payment.getUser(), payment.getTest());
        }
    }

    private Payment findPaymentForWebhook(WebhookData webhookData) {
        // Primary: look up by paymentId passed as requiredField from Flutter SDK
        Map<String, Object> fields = webhookData.getFields();
        if (fields != null && fields.get("paymentId") != null) {
            String paymentIdStr = fields.get("paymentId").toString();
            try {
                UUID paymentId = UUID.fromString(paymentIdStr);
                return paymentRepository.findByPaymentId(paymentId).orElse(null);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid paymentId in webhook fields: {}", paymentIdStr);
            }
        }

        log.error("Cannot match webhook to payment: no paymentId in fields. transactionId={}",
                webhookData.getTransactionId());
        return null;
    }

    @Transactional
    protected void grantTestAccess(User user, Test test) {
        UserTestAccess access = accessRepository.findByUserIdAndTestId(user.getId(), test.getId())
                .orElseGet(() -> UserTestAccess.builder().user(user).test(test).build());
        access.setGrantedAt(LocalDateTime.now());
        access.setExpiresAt(null); // a purchase grants permanent access
        accessRepository.save(access);

        log.info("Test access granted (permanent): userId={}, testId={}", user.getId(), test.getId());
    }

    public CreatePaymentResponse getPaymentStatus(UUID paymentId, Long userId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new AppException("Платёж не найден.", "Төлөм табылган жок."));

        if (!payment.getUser().getId().equals(userId)) {
            throw new AppException("Нет доступа.", "Мүмкүнчүлүк жок.");
        }

        return new CreatePaymentResponse(payment.getPaymentId(), payment.getPaymentUrl(), payment.getStatus().name());
    }
}