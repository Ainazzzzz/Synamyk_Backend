package synamyk.dto.attempt;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PassageView {
    private Long id;
    /** 1-based number: «1-тексттин суроолору». */
    private Integer number;
    private String title;
    /** Plain text; lines are separated by '\n'. The client numbers every 5th line (5, 10, 15...). */
    private String text;
    private String imageUrl;
    private List<Long> questionIds;
}
