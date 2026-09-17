package synamyk.dto.school;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SchoolRequest {
    @NotNull
    private Long districtId;
    @NotBlank
    private String name;
    private String nameKy;
    private Boolean active = true;
}
