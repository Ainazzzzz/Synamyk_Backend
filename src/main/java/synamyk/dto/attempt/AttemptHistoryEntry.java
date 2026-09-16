package synamyk.dto.attempt;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AttemptHistoryEntry {
    private Long attemptId;
    private Long testId;
    private String testTitle;
    private Integer ortScore;
    private Integer maxScore;
    private Integer correctAnswers;
    private Integer totalQuestions;
    private Double percentage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
