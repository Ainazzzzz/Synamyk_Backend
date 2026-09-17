package synamyk.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProductRequest {
    @NotNull
    @DecimalMin("0")
    private BigDecimal price;
    @DecimalMin("0")
    private BigDecimal oldPrice;
    @NotNull
    private Boolean active;
}
