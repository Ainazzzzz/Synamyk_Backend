package synamyk.dto.school;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "«Мектептер рейтинги» района")
public class SchoolRatingResponse {
    private Long districtId;
    private String districtName;
    @Schema(description = "SCORE | ACTIVITY | GROWTH")
    private String sort;
    private Integer minStudents;
    @Schema(description = "Школа текущего пользователя (карточка «Рейтингге кирүүгө: 1/3 окуучу»), null если школа не выбрана или из другого района")
    private MySchoolResponse mySchool;
    @Schema(description = "Сначала школы в рейтинге (по значению), затем набирающие учеников")
    private List<Entry> entries;

    @Data
    @Builder
    public static class Entry {
        @Schema(description = "Место; null если школа ещё не в рейтинге")
        private Integer rank;
        private Long schoolId;
        private String name;
        private Long studentCount;
        private Boolean inRating;
        @Schema(description = "SCORE — средний лучший балл ОРТ; ACTIVITY — пройденных тестов за 30 дней; GROWTH — средний прирост балла")
        private Double value;
        private Boolean isMine;
    }
}
