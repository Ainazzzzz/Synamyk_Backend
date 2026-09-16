package synamyk.enums;

/** Tabs of the school rating screen: «Упай» / «Активдүүлүк» / «Өсүш». */
public enum SchoolRatingSort {
    /** Average best ОРТ score of the school's students. */
    SCORE,
    /** Completed full-test attempts over the last 30 days. */
    ACTIVITY,
    /** Average improvement: best score of the last 30 days minus best score before that. */
    GROWTH
}
