package synamyk.enums;

/** Catalogue products with an admin-configurable price. */
public enum ProductCode {
    ALL_TESTS,
    ALL_TEXTS;

    public PaymentProduct toPaymentProduct() {
        return this == ALL_TESTS ? PaymentProduct.ALL_TESTS : PaymentProduct.ALL_TEXTS;
    }
}
