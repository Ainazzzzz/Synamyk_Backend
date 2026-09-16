package synamyk.enums;

/**
 * How a question is laid out on the client.
 * <ul>
 *   <li>{@code STANDARD} — text (may contain LaTeX {@code $...$}) + optional image/figure + options.</li>
 *   <li>{@code COMPARISON} — ОРТ «Математика 1»: two boxes «Колонка А» / «Колонка Б» and the four
 *       fixed options (А больше / Б больше / равны / нельзя определить).</li>
 * </ul>
 */
public enum QuestionType {
    STANDARD,
    COMPARISON
}
