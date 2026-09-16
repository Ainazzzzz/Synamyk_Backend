package synamyk.dto.school;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SchoolDto {
    private Long id;
    private Long districtId;
    private String name;
    private String nameKy;
    private Boolean active;
}
