package synamyk.dto.attempt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Старт теста целиком")
public class StartAttemptRequest {

    @Schema(description = "false — продолжить незавершённую попытку, если она есть (иначе создать новую). "
            + "true — «Начать заново»: незавершённая попытка закрывается, создаётся новая.", example = "false")
    private Boolean restart = false;
}
