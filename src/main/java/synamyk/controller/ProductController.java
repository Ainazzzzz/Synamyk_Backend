package synamyk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import synamyk.dto.product.AppConfigResponse;
import synamyk.dto.product.ProductResponse;
import synamyk.dto.text.ReadingTextDetail;
import synamyk.dto.text.ReadingTextListItem;
import synamyk.entities.User;
import synamyk.enums.ProductCode;
import synamyk.service.ProductService;
import synamyk.service.ReadingTextService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Продукты и тексты", description = "«Купить все тесты», «Открыть все тексты», библиотека текстов для чтения, конфиг приложения.")
public class ProductController {

    private final ProductService productService;
    private final ReadingTextService readingTextService;

    @GetMapping("/app/config")
    @Operation(summary = "Конфиг приложения (публично)", description = "Дата ОРТ для «N күн M саат калды», максимальный и пороговый балл.")
    public ResponseEntity<AppConfigResponse> config() {
        return ResponseEntity.ok(productService.config());
    }

    @GetMapping("/products")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Каталожные продукты", description = "Цена, доступен ли к покупке, куплен ли уже. Покупка: POST /api/payments/init?product=CODE.")
    public ResponseEntity<List<ProductResponse>> products(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(productService.list(user.getId(), user.getLanguage()));
    }

    @GetMapping("/products/{code}")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Продукт для экрана оплаты")
    public ResponseEntity<ProductResponse> product(@PathVariable ProductCode code, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(productService.get(code, user.getId(), user.getLanguage()));
    }

    @GetMapping("/texts")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Список текстов («Тексттер»)", description = "`hasAccess=false` — показать замок и экран покупки ALL_TEXTS.")
    public ResponseEntity<List<ReadingTextListItem>> texts(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingTextService.list(user.getId(), user.getLanguage()));
    }

    @GetMapping("/texts/{id}")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Текст для чтения", description = "Содержимое и ссылка на PDF (действует 1 час). 400 — нет доступа.")
    public ResponseEntity<ReadingTextDetail> text(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(readingTextService.get(id, user.getId(), user.getLanguage()));
    }
}
