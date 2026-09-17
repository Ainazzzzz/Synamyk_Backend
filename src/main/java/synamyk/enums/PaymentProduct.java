package synamyk.enums;

/** What a {@link synamyk.entities.Payment} buys. */
public enum PaymentProduct {
    /** Whole test (all its sections). */
    TEST,
    /** A single sub-test (legacy independent purchase). */
    SUB_TEST,
    /** One-time payment that unlocks every test. */
    ALL_TESTS,
    /** One-time payment that unlocks every reading text. */
    ALL_TEXTS
}
