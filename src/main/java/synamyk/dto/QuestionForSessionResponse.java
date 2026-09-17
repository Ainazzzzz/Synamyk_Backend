package synamyk.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class QuestionForSessionResponse {
    private Long questionId;
    private Integer index;          // 0-based
    private Integer totalQuestions;
    private String sectionName;
    private String text;
    private String imageUrl;
    /** STANDARD | COMPARISON */
    private String questionType;
    private String columnA;
    private String columnB;
    /** Coordinate plane / geometry drawing, null if none. */
    private Map<String, Object> figure;
    private Long passageId;
    private String passageText;
    private Integer pointValue;
    private List<AnswerOptionResponse> options;
    private Long remainingSeconds;
    private List<Long> selectedOptionIds;  // empty if not answered yet
    private Boolean isSkipped;
}