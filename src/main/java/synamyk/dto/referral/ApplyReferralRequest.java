package synamyk.dto.referral;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyReferralRequest {
    @NotBlank
    private String code;
}
