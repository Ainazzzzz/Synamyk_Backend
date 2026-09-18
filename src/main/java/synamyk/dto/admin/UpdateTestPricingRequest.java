package synamyk.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Цена теста. Разделы отдельно не продаются: покупается тест целиком.")
public class UpdateTestPricingRequest {

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    @Schema(description = "Цена всего теста. 0 — тест бесплатный.", example = "1000.00")
    private BigDecimal price;
}
