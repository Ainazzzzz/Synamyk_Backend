package synamyk.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Full question info for admin (all languages included)")
public class AdminQuestionResponse {
    private Long id;
    private String sectionName;
    private String sectionNameKy;
    private String text;
    private String textKy;
    private String imageUrl;
    private String questionType;
    private String columnA;
    private String columnAKy;
    private String columnB;
    private String columnBKy;
    private java.util.Map<String, Object> figure;
    private Long passageId;
    private String explanation;
    private String explanationKy;
    private Integer orderIndex;
    private Integer pointValue;
    private Boolean active;
    private List<OptionResponse> options;

    @Data
    @Builder
    @Schema(description = "Answer option info for admin")
    public static class OptionResponse {
        private Long id;
        private String label;
        private String text;
        private String textKy;
        private Boolean isCorrect;
        private Integer orderIndex;
    }
}