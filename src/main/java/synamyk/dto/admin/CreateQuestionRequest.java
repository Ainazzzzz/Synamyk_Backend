package synamyk.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import synamyk.enums.QuestionType;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Request to create or update a question with answer options")
public class CreateQuestionRequest {

    @NotBlank
    @Schema(description = "Question text in Russian", example = "Чему равно 2 + 2?")
    private String text;

    @Schema(description = "Question text in Kyrgyz")
    private String textKy;

    @Schema(description = "Optional section name in Russian (e.g. for ОРТ parts)", example = "1-часть: Математика")
    private String sectionName;

    @Schema(description = "Optional section name in Kyrgyz")
    private String sectionNameKy;

    @Schema(description = "URL of an image attached to the question (optional)")
    private String imageUrl;

    @Schema(description = "Explanation of the correct answer in Russian")
    private String explanation;

    @Schema(description = "Explanation of the correct answer in Kyrgyz")
    private String explanationKy;

    @Schema(description = "Display order within the sub-test (0-based)", example = "0")
    private Integer orderIndex = 0;

    @Schema(description = "Points awarded for a correct answer", example = "1")
    private Integer pointValue = 1;

    @Size(min = 2, max = 6)
    @Schema(description = "Answer options (2–6). At least one must have isCorrect = true. Multiple correct options are allowed. "
            + "May be omitted for COMPARISON questions when comparisonAnswer is given.")
    private List<AnswerOptionRequest> options;

    @Schema(description = "STANDARD (default) or COMPARISON («Колонка А» / «Колонка Б»)", example = "STANDARD")
    private QuestionType questionType = QuestionType.STANDARD;

    @Schema(description = "[COMPARISON] Колонка А (RU), LaTeX allowed", example = "$\\frac{7}{4} - \\frac{3}{4}$")
    private String columnA;
    private String columnAKy;

    @Schema(description = "[COMPARISON] Колонка Б (RU), LaTeX allowed", example = "$\\frac{7}{8} - \\frac{1}{8}$")
    private String columnB;
    private String columnBKy;

    @Schema(description = "[COMPARISON] Generate the 4 standard options automatically with this one correct: "
            + "A_GREATER | B_GREATER | EQUAL | UNDETERMINED. Ignored when options are given.")
    private ComparisonAnswer comparisonAnswer;

    @Schema(description = "Figure: coordinate plane or geometry drawing (see admin prompt §figure). null = none.")
    private Map<String, Object> figure;

    @Schema(description = "Reading passage id (same sub-test) for «Окуу жана түшүнүү» questions; null = none")
    private Long passageId;

    public enum ComparisonAnswer { A_GREATER, B_GREATER, EQUAL, UNDETERMINED }

    @Data
    @Schema(description = "A single answer option")
    public static class AnswerOptionRequest {

        @NotBlank
        @Schema(description = "Option label: А, Б, В, Г, Д", example = "А")
        private String label;

        @NotBlank
        @Schema(description = "Option text in Russian", example = "4")
        private String text;

        @Schema(description = "Option text in Kyrgyz")
        private String textKy;

        @Schema(description = "Whether this option is the correct answer", example = "false")
        private Boolean isCorrect = false;

        @Schema(description = "Display order of this option", example = "0")
        private Integer orderIndex = 0;
    }
}