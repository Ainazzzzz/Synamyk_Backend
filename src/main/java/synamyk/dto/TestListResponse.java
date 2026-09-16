package synamyk.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TestListResponse {
    private Long id;
    private String title;
    private String description;
    private String iconUrl;
    private BigDecimal price;
    private Integer subTestCount;
    private Integer completedSubTestCount; // sub-tests the user has completed at least once
    private Integer progressPercent;       // completedSubTestCount / subTestCount * 100
    private Integer totalDurationMinutes;  // sum of section durations («03 саат 35 мүн»)
    private Integer totalQuestions;
    private Integer maxScore;              // ОРТ max (245)
    private Boolean isFree;                // «Акысыз» badge
    private Boolean hasAccess;             // can start the whole test now (false → «Премиум» lock)
    private Integer bestOrtScore;          // best completed attempt, null if none
    private Integer completedAttempts;
    private Long resumableAttemptId;       // unfinished attempt → «Продолжить», null if none
}
