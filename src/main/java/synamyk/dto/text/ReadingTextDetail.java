package synamyk.dto.text;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReadingTextDetail {
    private Long id;
    private String title;
    private String content;
    /** Presigned PDF URL (valid 1 hour), null if the text has no PDF. */
    private String pdfUrl;
}
