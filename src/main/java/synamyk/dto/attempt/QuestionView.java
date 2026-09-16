package synamyk.dto.attempt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import synamyk.dto.AnswerOptionResponse;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "Вопрос внутри раздела. Тексты могут содержать LaTeX: $...$ (строчная) и $$...$$ (блочная формула).")
public class QuestionView {
    private Long questionId;
    @Schema(description = "0-based позиция в разделе")
    private Integer index;
    @Schema(description = "Номер для отображения «N-суроо» (1-based)")
    private Integer number;
    @Schema(description = "ID текста для чтения, к которому относится вопрос (null — обычный вопрос)")
    private Long passageId;
    @Schema(description = "Тема вопроса (используется в разборе «Темалар боюнча»)")
    private String sectionName;
    @Schema(description = "STANDARD | COMPARISON (Колонка А / Колонка Б)")
    private String questionType;
    private String text;
    @Schema(description = "[COMPARISON] Колонка А")
    private String columnA;
    @Schema(description = "[COMPARISON] Колонка Б")
    private String columnB;
    private String imageUrl;
    @Schema(description = "Чертёж: координатная плоскость или геометрическая фигура (JSON), null если нет")
    private Map<String, Object> figure;
    private Integer pointValue;
    private List<AnswerOptionResponse> options;
    @Schema(description = "Уже выбранные варианты (пусто — не отвечен)")
    private List<Long> selectedOptionIds;
}
