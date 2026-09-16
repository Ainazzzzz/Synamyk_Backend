package synamyk.dto.attempt;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SaveAnswerRequest {
    @NotNull
    private Long questionId;
    /** null or empty — clear the answer. */
    private List<Long> selectedOptionIds;
}
