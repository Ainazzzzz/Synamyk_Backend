package synamyk.dto.school;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StudentRatingResponse {
    @Schema(description = "SCHOOL | DISTRICT | REGION | ALL")
    private String scope;
    private List<Entry> entries;
    @Schema(description = "Текущий пользователь, если он есть в выборке")
    private Entry me;

    @Data
    @Builder
    public static class Entry {
        private Integer rank;
        private Long userId;
        private String fullName;
        private String avatarUrl;
        private String schoolName;
        @Schema(description = "Лучший балл ОРТ")
        private Integer score;
        private Boolean isMe;
    }
}
