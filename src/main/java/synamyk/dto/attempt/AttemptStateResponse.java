package synamyk.dto.attempt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "Состояние попытки прохождения теста целиком")
public class AttemptStateResponse {
    private Long attemptId;
    private Long testId;
    private String testTitle;
    @Schema(description = "IN_PROGRESS | PAUSED | COMPLETED | ABANDONED")
    private String status;
    @Schema(description = "true — продолжена существующая попытка")
    private Boolean isResumed;
    private LocalDateTime startedAt;
    private Integer totalDurationMinutes;
    private Integer totalQuestions;
    private Integer maxScore;
    private Integer currentSectionIndex;
    private Integer totalSections;
    @Schema(description = "Секунд до конца всего теста")
    private Long totalRemainingSeconds;
    private List<SectionState> sections;

    @Data
    @Builder
    public static class SectionState {
        private Integer index;
        private Long subTestId;
        private String title;
        private String iconUrl;
        private Integer durationMinutes;
        private Integer questionCount;
        @Schema(description = "NOT_STARTED | IN_PROGRESS | PAUSED | COMPLETED | EXPIRED")
        private String status;
        private Integer answeredCount;
        @Schema(description = "Для текущего раздела — секунд осталось, для не начатых — полная длительность, для завершённых — 0")
        private Long remainingSeconds;
    }
}
