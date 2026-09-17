package synamyk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import synamyk.dto.referral.ApplyReferralRequest;
import synamyk.dto.referral.RedeemRewardRequest;
import synamyk.dto.referral.ReferralInfoResponse;
import synamyk.entities.User;
import synamyk.service.ReferralService;

@RestController
@RequestMapping("/api/referrals")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "Пригласи друга", description = """
        Друг регистрируется с твоим кодом → совершает первую покупку → тебе начисляется награда →
        выбираешь любой платный тест и он открывается бесплатно навсегда.
        """)
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/me")
    @Operation(summary = "Мой код, ссылка, приглашённые и награды", description = "Код генерируется при первом запросе.")
    public ResponseEntity<ReferralInfoResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(referralService.getInfo(user.getId(), user.getLanguage()));
    }

    @PostMapping("/apply")
    @Operation(summary = "Ввести код друга", description = "Один раз и только до первой покупки. Нельзя свой код.")
    public ResponseEntity<ReferralInfoResponse> apply(@Valid @RequestBody ApplyReferralRequest request,
                                                      @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(referralService.apply(user.getId(), request.getCode(), user.getLanguage()));
    }

    @PostMapping("/rewards/redeem")
    @Operation(summary = "Открыть тест за награду", description = "Тратит одну доступную награду и навсегда открывает выбранный тест.")
    public ResponseEntity<ReferralInfoResponse> redeem(@Valid @RequestBody RedeemRewardRequest request,
                                                       @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(referralService.redeem(user.getId(), request.getTestId(), user.getLanguage()));
    }
}
