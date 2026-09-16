package synamyk.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the current user's payment history. */
public record PaymentHistoryEntry(
        UUID paymentId,
        String product,         // TEST | SUB_TEST | ALL_TESTS | ALL_TEXTS
        Long testId,            // null for ALL_TESTS / ALL_TEXTS
        String testTitle,
        Long subTestId,         // null for a whole-test (bundle) purchase
        String subTestTitle,    // null for a whole-test (bundle) purchase, localized
        BigDecimal amount,
        String status,          // PENDING | COMPLETED | EXPIRED | CANCELLED
        String receiptNumber,   // null until paid
        String paymentUrl,      // useful to resume a PENDING payment
        LocalDateTime createdAt,
        LocalDateTime paidAt    // null unless COMPLETED
) {}
