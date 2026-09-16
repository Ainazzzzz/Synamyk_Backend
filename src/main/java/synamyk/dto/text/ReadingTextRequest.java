package synamyk.dto.text;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReadingTextRequest {
    @NotBlank
    private String title;
    private String titleKy;
    private String content;
    private String contentKy;
    /** MinIO key returned by POST /api/admin/texts/pdf, or an external URL. */
    private String pdfUrl;
    private Boolean free = false;
    private Integer orderIndex = 0;
    private Boolean active = true;
}
