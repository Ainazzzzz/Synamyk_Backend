package synamyk.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to create or update a sub-test")
public class CreateSubTestRequest {

    @NotBlank
    @Schema(description = "Sub-test title in Russian", example = "Бесплатный уровень")
    private String title;

    @Schema(description = "Sub-test title in Kyrgyz")
    private String titleKy;

    @NotBlank
    @Schema(description = "Level display name in Russian", example = "1-уровень")
    private String levelName;

    @Schema(description = "Level display name in Kyrgyz")
    private String levelNameKy;

    @NotNull
    @Schema(description = "Level order for sorting (lower = first)", example = "1")
    private Integer levelOrder;

    @NotNull
    @Schema(description = "Duration of the sub-test in minutes", example = "30")
    private Integer durationMinutes;

    @jakarta.validation.constraints.Min(0)
    @Schema(description = "ОРТ points of this section. null = share of the test maxScore proportional to the section's points "
            + "(default ОРТ layout: Математика 60 вопросов → 98, остальные разделы по 30 → 49).", example = "49")
    private Integer maxScore;

    @Schema(description = "Section icon (MinIO key or URL)")
    private String iconUrl;
}