package synamyk.dto.attempt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "Результат теста: балл ОРТ + разбивка по разделам и темам")
public class AttemptResultResponse {
    private Long attemptId;
    private Long testId;
    private String testTitle;
    private String userFullName;
    private LocalDateTime completedAt;
    @Schema(description = "Итоговый балл по шкале ОРТ", example = "163")
    private Integer ortScore;
    @Schema(description = "Максимальный балл теста (ОРТ — 245)", example = "245")
    private Integer maxScore;
    @Schema(description = "Пороговый балл ОРТ", example = "110")
    private Integer thresholdScore;
    private Boolean passedThreshold;
    private Integer totalQuestions;
    private Integer correctAnswers;
    private Integer wrongAnswers;
    private Integer skippedAnswers;
    private Integer earnedPoints;
    private Integer totalPoints;
    @Schema(example = "66.7")
    private Double percentage;
    private Long timeTakenSeconds;
    private String motivationalMessage;
    @Schema(description = "«Бөлүктөр боюнча»")
    private List<SectionResult> sections;
    @Schema(description = "«Темалар боюнча»")
    private List<TopicResult> topics;

    @Data
    @Builder
    public static class SectionResult {
        private Long subTestId;
        private String title;
        @Schema(description = "ID сессии раздела — для детального разбора GET /api/sessions/{sessionId}/result и ИИ-разбора")
        private Long sessionId;
        @Schema(description = "COMPLETED | EXPIRED | NOT_STARTED")
        private String status;
        private Integer totalQuestions;
        private Integer correctAnswers;
        private Integer wrongAnswers;
        private Integer skippedAnswers;
        private Integer earnedPoints;
        private Integer totalPoints;
        private Double percentage;
        @Schema(description = "Баллы ОРТ за раздел")
        private Integer ortScore;
        @Schema(description = "Максимум баллов ОРТ за раздел")
        private Integer maxScore;
    }

    @Data
    @Builder
    public static class TopicResult {
        private String topic;
        private Long subTestId;
        private String sectionTitle;
        private Integer totalQuestions;
        private Integer correctAnswers;
        private Double percentage;
    }
}
