package synamyk.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PassageResponse {
    private Long id;
    private Long subTestId;
    private String title;
    private String titleKy;
    private String text;
    private String textKy;
    private String imageUrl;
    private Integer orderIndex;
    private Boolean active;
    private Long questionCount;
}
