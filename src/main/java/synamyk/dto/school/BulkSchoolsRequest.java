package synamyk.dto.school;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Массовое добавление школ района (по одному названию на строку). Дубликаты по названию пропускаются.")
public class BulkSchoolsRequest {
    @NotNull
    private Long districtId;
    @NotEmpty
    @Size(max = 1000)
    private List<String> names;
}
