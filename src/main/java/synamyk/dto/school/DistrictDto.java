package synamyk.dto.school;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DistrictDto {
    private Long id;
    private Long regionId;
    private String name;
    private String nameKy;
    private Boolean active;
}
