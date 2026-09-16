package synamyk.dto.school;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DistrictRequest {
    @NotNull
    private Long regionId;
    @NotBlank
    private String name;
    private String nameKy;
    private Boolean active = true;
}
