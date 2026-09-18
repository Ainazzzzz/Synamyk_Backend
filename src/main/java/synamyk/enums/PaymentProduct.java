package synamyk.enums;

/** What a {@link synamyk.entities.Payment} buys. */
public enum PaymentProduct {
    /** Whole test — the only way a test is sold. */
    TEST,
    /** One-time payment that unlocks every test. */
    ALL_TESTS,
    /** One-time payment that unlocks every reading text. */
    ALL_TEXTS
}
