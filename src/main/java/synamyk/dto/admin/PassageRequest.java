package synamyk.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Текст для чтения внутри раздела («Окуу жана түшүнүү»). Строки разделяются \\n — клиент нумерует каждую 5-ю строку.")
public class PassageRequest {
    private String title;
    private String titleKy;
    @NotBlank
    private String text;
    private String textKy;
    private String imageUrl;
    private Integer orderIndex = 0;
}
