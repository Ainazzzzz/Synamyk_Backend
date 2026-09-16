package synamyk.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExamDateRequest {
    @Schema(description = "Дата и время ОРТ (Asia/Bishkek, без зоны). null — скрыть счётчик.", example = "2027-05-15T08:00:00")
    private LocalDateTime date;
}
