package synamyk.dto.school;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MySchoolResponse {
    private Long schoolId;
    private String schoolName;
    private Long districtId;
    private String districtName;
    private Long regionId;
    private String regionName;
    @Schema(description = "Сколько учеников школы зарегистрировано")
    private Long studentCount;
    @Schema(description = "Нужно учеников для попадания в рейтинг", example = "3")
    private Integer minStudents;
    private Boolean inRating;
    @Schema(description = "Сколько ещё одноклассников пригласить (0 — уже в рейтинге)")
    private Integer studentsNeeded;
}
