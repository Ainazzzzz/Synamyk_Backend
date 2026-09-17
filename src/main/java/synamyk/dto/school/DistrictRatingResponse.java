package synamyk.dto.school;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DistrictRatingResponse {
    private Long regionId;
    private String regionName;
    private List<Entry> entries;

    @Data
    @Builder
    public static class Entry {
        private Integer rank;
        private Long districtId;
        private String name;
        private Long studentsWithResults;
        private Double averageScore;
        private Boolean isMine;
    }
}
