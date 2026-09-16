package synamyk.dto.game;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Игровой рейтинг (Эло, как в шахматах): победа — плюс, поражение — минус")
public class GameRatingResponse {
    private Long userId;
    private String fullName;
    private String avatarUrl;
    @Schema(example = "1034")
    private Integer rating;
    private Integer peakRating;
    @Schema(description = "Место в общем рейтинге; null пока не сыграна ни одна игра")
    private Long rank;
    private Integer gamesPlayed;
    private Integer wins;
    private Integer losses;
    private Integer draws;
    private Integer winStreak;
    @Schema(description = "BRONZE | SILVER | GOLD | PLATINUM | DIAMOND")
    private String league;
    private String leagueName;
    @Schema(description = "Рейтинг, с которого начинается следующая лига; null для DIAMOND")
    private Integer nextLeagueRating;
}
