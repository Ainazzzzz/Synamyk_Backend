package synamyk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import synamyk.dto.attempt.*;
import synamyk.entities.User;
import synamyk.service.TestAttemptService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Тест целиком (ОРТ)", description = """
        Прохождение теста как одного целого: разделы (подтесты) идут подряд, у каждого свой таймер,
        результат — один балл по шкале ОРТ (макс. 245) с разбивкой по разделам и темам.

        **Цикл:** `GET /tests/{id}/attempts/resumable` → (диалог «Продолжить / Начать заново») →
        `POST /tests/{id}/attempts` → `GET /attempts/{id}/section` → `PUT /attempts/{id}/answers` (сколько угодно раз, в любом порядке) →
        `POST /attempts/{id}/section/finish` → … следующий раздел … → после последнего раздела статус `COMPLETED` →
        `GET /attempts/{id}/result`.

        При сворачивании/закрытии приложения вызывайте `POST /attempts/{id}/pause` — таймер раздела замораживается.
        """)
@SecurityRequirement(name = "Bearer")
public class TestAttemptController {

    private final TestAttemptService attemptService;

    @GetMapping("/tests/{testId}/attempts/resumable")
    @Operation(summary = "Незавершённая попытка по тесту",
            description = "200 — есть попытка, которую можно продолжить (показать диалог «Продолжить / Начать заново»). 204 — нет.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Есть незавершённая попытка"),
            @ApiResponse(responseCode = "204", description = "Нет незавершённой попытки")
    })
    public ResponseEntity<AttemptStateResponse> resumable(@PathVariable Long testId, @AuthenticationPrincipal User user) {
        return attemptService.findResumable(testId, user.getId(), user.getLanguage())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/tests/{testId}/attempts")
    @Operation(summary = "Начать / продолжить / начать заново тест",
            description = "`restart=false` — продолжить незавершённую попытку (или создать новую, если её нет). "
                    + "`restart=true` — закрыть незавершённую и начать с нуля. Требует доступа ко всему тесту.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние попытки"),
            @ApiResponse(responseCode = "400", description = "Нет доступа (тест не куплен) или в тесте нет разделов")
    })
    public ResponseEntity<AttemptStateResponse> start(@PathVariable Long testId,
                                                      @RequestBody(required = false) StartAttemptRequest request,
                                                      @AuthenticationPrincipal User user) {
        boolean restart = request != null && Boolean.TRUE.equals(request.getRestart());
        return ResponseEntity.ok(attemptService.start(testId, user.getId(), restart, user.getLanguage()));
    }

    @GetMapping("/attempts/{attemptId}")
    @Operation(summary = "Состояние попытки", description = "Разделы со статусами, текущий раздел, оставшееся время.")
    public ResponseEntity<AttemptStateResponse> state(@PathVariable Long attemptId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attemptService.getState(attemptId, user.getId(), user.getLanguage()));
    }

    @GetMapping("/attempts/{attemptId}/section")
    @Operation(summary = "Вопросы текущего раздела",
            description = "Все вопросы раздела разом (+ тексты для чтения) с уже сохранёнными ответами. "
                    + "Первый вызов запускает таймер раздела. Если время раздела вышло — раздел закрывается "
                    + "автоматически и возвращается следующий. Если тест уже завершён — 400.")
    public ResponseEntity<SectionQuestionsResponse> section(@PathVariable Long attemptId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attemptService.getCurrentSection(attemptId, user.getId(), user.getLanguage()));
    }

    @PutMapping("/attempts/{attemptId}/answers")
    @Operation(summary = "Сохранить ответ",
            description = "Сохраняет/меняет/очищает ответ на любой вопрос текущего раздела. "
                    + "Пустой `selectedOptionIds` — снять ответ. Если время раздела вышло — 400, "
                    + "клиент должен перезапросить `GET /section`.")
    public ResponseEntity<SaveAnswerResponse> saveAnswer(@PathVariable Long attemptId,
                                                         @Valid @RequestBody SaveAnswerRequest request,
                                                         @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attemptService.saveAnswer(attemptId, user.getId(), request));
    }

    @PostMapping("/attempts/{attemptId}/section/finish")
    @Operation(summary = "Завершить текущий раздел",
            description = "Переходит к следующему разделу. После последнего раздела попытка получает статус COMPLETED — "
                    + "тогда откройте `GET /attempts/{id}/result`.")
    public ResponseEntity<AttemptStateResponse> finishSection(@PathVariable Long attemptId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attemptService.finishSection(attemptId, user.getId(), user.getLanguage()));
    }

    @PostMapping("/attempts/{attemptId}/pause")
    @Operation(summary = "Пауза (выход из теста / сворачивание приложения)",
            description = "Таймер текущего раздела останавливается до следующего `POST /tests/{id}/attempts` или `GET /section`.")
    public ResponseEntity<AttemptStateResponse> pause(@PathVariable Long attemptId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attemptService.pause(attemptId, user.getId(), user.getLanguage()));
    }

    @PostMapping("/attempts/{attemptId}/finish")
    @Operation(summary = "Завершить весь тест досрочно",
            description = "Незаконченные и не начатые разделы засчитываются как без ответов. Возвращает результат.")
    public ResponseEntity<AttemptResultResponse> finish(@PathVariable Long attemptId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attemptService.finish(attemptId, user.getId(), user.getLanguage()));
    }

    @GetMapping("/attempts/{attemptId}/result")
    @Operation(summary = "Результат теста",
            description = "Балл ОРТ, пороговый балл, разбивка «Бөлүктөр боюнча» (баллы и % по разделам) и «Темалар боюнча».")
    public ResponseEntity<AttemptResultResponse> result(@PathVariable Long attemptId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attemptService.getResult(attemptId, user.getId(), user.getLanguage()));
    }

    @GetMapping("/attempts/history")
    @Operation(summary = "История завершённых тестов («Жыйынтык»)", description = "От новых к старым.")
    public ResponseEntity<Page<AttemptHistoryEntry>> history(
            @Parameter(description = "Страница (с 0)") @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        int capped = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(attemptService.history(user.getId(), PageRequest.of(page, capped), user.getLanguage()));
    }
}
