package synamyk.dto.referral;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "«Пригласи друга»: если друг купит тест — тебе бесплатно откроется любой 1 тест")
public class ReferralInfoResponse {
    private String code;
    @Schema(description = "Ссылка-приглашение для шаринга")
    private String inviteLink;
    @Schema(description = "Готовый текст для кнопки «Бөлүшүү»")
    private String shareText;
    @Schema(description = "Сколько друзей зарегистрировалось по коду")
    private Long invitedCount;
    @Schema(description = "Сколько из них совершили покупку (= заработано наград)")
    private Long purchasedCount;
    @Schema(description = "Неиспользованные награды — столько тестов можно открыть бесплатно")
    private Integer availableRewards;
    @Schema(description = "Можно ли ещё ввести чужой код (нет пригласившего и нет покупок)")
    private Boolean canApplyCode;
    private String referredByName;
    private List<RewardEntry> rewards;

    @Data
    @Builder
    public static class RewardEntry {
        private Long rewardId;
        private String friendName;
        private LocalDateTime earnedAt;
        private Boolean available;
        private Long redeemedTestId;
        private String redeemedTestTitle;
        private LocalDateTime redeemedAt;
    }
}
