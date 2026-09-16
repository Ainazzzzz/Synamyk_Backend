package synamyk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompleteProfileRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotNull(message = "Region is required")
    private Long regionId;

    /** Optional: school (GET /api/regions/{regionId}/districts → GET /api/districts/{id}/schools). */
    private Long schoolId;

    /** Optional: friend's invite code («Пригласи друга»). */
    private String referralCode;
}