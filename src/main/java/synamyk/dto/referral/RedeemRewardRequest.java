package synamyk.dto.referral;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RedeemRewardRequest {
    @NotNull
    private Long testId;
}
