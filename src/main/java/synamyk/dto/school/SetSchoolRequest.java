package synamyk.dto.school;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetSchoolRequest {
    @NotNull
    private Long schoolId;
}
