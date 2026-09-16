package synamyk.dto.text;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReadingTextListItem {
    private Long id;
    private String title;
    private Integer orderIndex;
    private Boolean free;
    /** false → show lock and open the ALL_TEXTS purchase screen. */
    private Boolean hasAccess;
    private Boolean hasPdf;
}
