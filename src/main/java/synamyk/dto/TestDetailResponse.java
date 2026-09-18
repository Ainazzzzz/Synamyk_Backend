package synamyk.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TestDetailResponse {
    private Long id;
    private String title;
    private String description;
    private String iconUrl;
    private BigDecimal price;
    private Boolean hasPaidAccess;      // user already paid for this test
    private Boolean isFree;             // price is 0 or a free window is open now
    private Boolean hasAccess;          // the whole test can be started now
    private LocalDateTime freeUntil;    // end of the active free window, null if n/a or open-ended
    private Integer totalDurationMinutes;
    private Integer totalQuestions;
    private Integer maxScore;
    private Integer bestOrtScore;
    private Long resumableAttemptId;
    private Integer resumableSectionIndex;
    private List<SubTestResponse> subTests;
}
