package synamyk.dto.product;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppConfigResponse {
    /** Date of the next ОРТ («Жалпы республикалык тестирлөөгө N күн M саат калды»), null if not set. */
    private LocalDateTime ortExamDate;
    /** Seconds left until the exam, 0 if passed, null if not set. */
    private Long secondsUntilExam;
    private Integer ortMaxScore;
    private Integer ortThresholdScore;
    /** Students a school needs to appear in the school rating. */
    private Integer schoolRatingMinStudents;
}
