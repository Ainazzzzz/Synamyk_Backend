package synamyk.dto.attempt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "Текущий раздел попытки со всеми вопросами (экран со списком вопросов и таймером раздела)")
public class SectionQuestionsResponse {
    private Long attemptId;
    private Long sessionId;
    private Integer sectionIndex;
    private Integer totalSections;
    private Boolean isLastSection;
    private Long subTestId;
    private String title;
    private Integer durationMinutes;
    private LocalDateTime expiresAt;
    @Schema(description = "Секунд до конца раздела")
    private Long remainingSeconds;
    @Schema(description = "Секунд до конца всего теста (текущий раздел + длительность оставшихся разделов)")
    private Long totalRemainingSeconds;
    private Integer totalQuestions;
    private Integer answeredCount;
    @Schema(description = "Тексты для чтения раздела (пусто для разделов без текстов)")
    private List<PassageView> passages;
    private List<QuestionView> questions;
}
