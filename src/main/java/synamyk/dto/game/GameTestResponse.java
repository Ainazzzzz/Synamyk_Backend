package synamyk.dto.game;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Игровой тест")
public class GameTestResponse {

    @Schema(description = "ID теста", example = "1")
    private Long id;

    @Schema(description = "Название теста", example = "Математика: Арифметика")
    private String title;

    @Schema(description = "Описание теста", example = "Вопросы по базовой арифметике")
    private String description;

    @Schema(description = "Лимит времени на один вопрос (секунды)", example = "30")
    private Integer timeLimitSeconds;

    @Schema(description = "Количество вопросов за партию (0 = все)", example = "10")
    private Integer questionsPerGame;

    @Schema(description = "Активен ли тест", example = "true")
    private Boolean active;

    @Schema(description = "Количество активных вопросов в тесте", example = "25")
    private long questionCount;

    @Schema(description = "Список вопросов (только при запросе одного теста GET /{id})")
    private List<QuestionDetail> questions;

    @Data
    @Builder
    @Schema(description = "Вопрос игрового теста (только в ответе администратора, с полем correct)")
    public static class QuestionDetail {
        @Schema(description = "ID вопроса", example = "42")
        private Long id;

        @Schema(description = "Текст вопроса", example = "Чему равно среднее арифметическое чисел 10, 20, 30?")
        private String text;

        @Schema(description = "URL изображения (необязательно)")
        private String imageUrl;

        @Schema(description = "Чертёж (необязательно)")
        private java.util.Map<String, Object> figure;

        @Schema(description = "Порядковый номер", example = "0")
        private Integer orderIndex;

        @Schema(description = "Активен ли вопрос", example = "true")
        private Boolean active;

        @Schema(description = "Варианты ответа")
        private List<OptionDetail> options;
    }

    @Data
    @Builder
    @Schema(description = "Вариант ответа (только в ответе администратора, содержит поле correct)")
    public static class OptionDetail {
        @Schema(description = "ID варианта", example = "101")
        private Long id;

        @Schema(description = "Текст варианта", example = "20")
        private String text;

        @Schema(description = "Правильный ли ответ", example = "true")
        private Boolean correct;

        @Schema(description = "Порядковый номер", example = "0")
        private Integer orderIndex;
    }
}
