package synamyk.dto.attempt;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SaveAnswerResponse {
    private Long questionId;
    private List<Long> selectedOptionIds;
    private Integer answeredCount;
    private Integer totalQuestions;
    private Long remainingSeconds;
}
