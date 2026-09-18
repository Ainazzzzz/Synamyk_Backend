package synamyk.dto;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class SubTestResponse {
    private Long id;
    private String title;
    private String levelName;
    private Integer levelOrder;
    private Integer durationMinutes;
    private String iconUrl;
    private Integer maxScore;      // ОРТ points this section contributes
    private Long questionCount;
    private Boolean hasAccess;     // access is granted for the whole test, see AccessResolver
    private Boolean hasCompleted;  // true if user has completed this sub-test
    private Integer bestScore;     // best earnedPoints among COMPLETED sessions, null if none
    private Long bestSessionId;    // sessionId of that best COMPLETED attempt, null if none
    private Integer attemptsCount; // total number of attempts (any status)
}