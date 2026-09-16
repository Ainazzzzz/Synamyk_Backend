package synamyk.dto.product;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import synamyk.enums.ProductCode;

import java.time.LocalDateTime;

@Data
public class GrantAllAccessRequest {
    @NotNull
    private Long userId;
    @NotNull
    private ProductCode product;
    /** Days from now; null together with expiresAt = permanent. */
    private Integer durationDays;
    private LocalDateTime expiresAt;
}
