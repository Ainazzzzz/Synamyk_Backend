package synamyk.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to create or update a test")
public class CreateTestRequest {

    @NotBlank
    @Schema(description = "Title in Russian (required)", example = "Математика")
    private String title;

    @Schema(description = "Title in Kyrgyz", example = "Математика")
    private String titleKy;

    @Schema(description = "Description in Russian")
    private String description;

    @Schema(description = "Description in Kyrgyz")
    private String descriptionKy;

    @Schema(description = "URL to test icon image")
    private String iconUrl;

    @Schema(description = "Subject / category (e.g. Математика, ОРТ, Логика)", example = "Математика")
    private String subject;

    @NotNull
    @Schema(description = "Price to unlock all paid sub-tests", example = "500.00")
    private BigDecimal price;

    @jakarta.validation.constraints.Min(1)
    @Schema(description = "Max ОРТ score of the whole test (default 245)", example = "245")
    private Integer maxScore = 245;
}