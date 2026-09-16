package synamyk.dto.text;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminReadingTextResponse {
    private Long id;
    private String title;
    private String titleKy;
    private String content;
    private String contentKy;
    private String pdfKey;
    private String pdfUrl;
    private Boolean free;
    private Integer orderIndex;
    private Boolean active;
    private LocalDateTime createdAt;
}
