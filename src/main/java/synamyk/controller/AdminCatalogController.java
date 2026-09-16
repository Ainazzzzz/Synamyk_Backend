package synamyk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import synamyk.dto.MessageResponse;
import synamyk.dto.product.*;
import synamyk.dto.school.*;
import synamyk.dto.text.AdminReadingTextResponse;
import synamyk.dto.text.ReadingTextRequest;
import synamyk.entities.User;
import synamyk.entities.UserAllAccess;
import synamyk.enums.ProductCode;
import synamyk.exception.AppException;
import synamyk.repo.UserAllAccessRepository;
import synamyk.repo.UserRepository;
import synamyk.service.ProductService;
import synamyk.service.ReadingTextService;
import synamyk.service.SchoolService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "Админ — Каталог", description = "Цены «все тесты / все тексты», дата ОРТ, тексты для чтения, районы и школы. Требуется роль ADMIN.")
public class AdminCatalogController {

    private final ProductService productService;
    private final ReadingTextService readingTextService;
    private final SchoolService schoolService;
    private final UserRepository userRepository;
    private final UserAllAccessRepository allAccessRepository;

    // ===== PRODUCTS / SETTINGS =====

    @GetMapping("/products")
    @Operation(summary = "Каталожные продукты")
    public ResponseEntity<List<ProductResponse>> products() {
        return ResponseEntity.ok(productService.list(null, "RU"));
    }

    @PutMapping("/products/{code}")
    @Operation(summary = "Цена и активность продукта", description = "`active=true` требует цену > 0.")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable ProductCode code,
                                                         @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.update(code, request));
    }

    @PutMapping("/settings/ort-exam-date")
    @Operation(summary = "Дата ОРТ для счётчика на главной")
    public ResponseEntity<AppConfigResponse> setExamDate(@RequestBody ExamDateRequest request) {
        return ResponseEntity.ok(productService.setExamDate(request.getDate()));
    }

    @PostMapping("/all-access")
    @Transactional
    @Operation(summary = "Выдать доступ «все тесты» / «все тексты»", description = "Без срока — бессрочно.")
    public ResponseEntity<MessageResponse> grantAllAccess(@Valid @RequestBody GrantAllAccessRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException("Пользователь не найден.", "Колдонуучу табылган жок."));
        UserAllAccess access = allAccessRepository.findByUserIdAndProduct(user.getId(), request.getProduct())
                .orElseGet(() -> UserAllAccess.builder().user(user).product(request.getProduct()).build());
        LocalDateTime now = LocalDateTime.now();
        access.setGrantedAt(now);
        access.setExpiresAt(request.getExpiresAt() != null ? request.getExpiresAt()
                : request.getDurationDays() != null ? now.plusDays(request.getDurationDays()) : null);
        allAccessRepository.save(access);
        return ResponseEntity.ok(new MessageResponse(true, "Доступ выдан."));
    }

    @DeleteMapping("/all-access")
    @Transactional
    @Operation(summary = "Отозвать доступ «все тесты» / «все тексты»")
    public ResponseEntity<Void> revokeAllAccess(@RequestParam Long userId, @RequestParam ProductCode product) {
        allAccessRepository.deleteByUserIdAndProduct(userId, product);
        return ResponseEntity.noContent().build();
    }

    // ===== READING TEXTS =====

    @GetMapping("/texts")
    @Operation(summary = "Все тексты (включая скрытые)")
    public ResponseEntity<List<AdminReadingTextResponse>> texts() {
        return ResponseEntity.ok(readingTextService.adminList());
    }

    @PostMapping(value = "/texts/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить PDF", description = "Возвращает `{ \"key\": \"texts/uuid.pdf\" }` — передайте в `pdfUrl` текста.")
    public ResponseEntity<Map<String, String>> uploadPdf(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(Map.of("key", readingTextService.uploadPdf(file)));
    }

    @PostMapping("/texts")
    @Operation(summary = "Создать текст")
    public ResponseEntity<AdminReadingTextResponse> createText(@Valid @RequestBody ReadingTextRequest request) {
        return ResponseEntity.ok(readingTextService.create(request));
    }

    @PutMapping("/texts/{id}")
    @Operation(summary = "Изменить текст")
    public ResponseEntity<AdminReadingTextResponse> updateText(@PathVariable Long id,
                                                               @Valid @RequestBody ReadingTextRequest request) {
        return ResponseEntity.ok(readingTextService.update(id, request));
    }

    @DeleteMapping("/texts/{id}")
    @Operation(summary = "Скрыть текст")
    public ResponseEntity<Void> deleteText(@PathVariable Long id) {
        readingTextService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ===== DISTRICTS / SCHOOLS =====

    @GetMapping("/regions/{regionId}/districts")
    @Operation(summary = "Районы региона (включая неактивные)")
    public ResponseEntity<List<DistrictDto>> districts(@PathVariable Long regionId) {
        return ResponseEntity.ok(schoolService.adminDistricts(regionId));
    }

    @PostMapping("/districts")
    @Operation(summary = "Создать район")
    public ResponseEntity<DistrictDto> createDistrict(@Valid @RequestBody DistrictRequest request) {
        return ResponseEntity.ok(schoolService.saveDistrict(null, request));
    }

    @PutMapping("/districts/{id}")
    @Operation(summary = "Изменить район", description = "`active=false` — скрыть.")
    public ResponseEntity<DistrictDto> updateDistrict(@PathVariable Long id, @Valid @RequestBody DistrictRequest request) {
        return ResponseEntity.ok(schoolService.saveDistrict(id, request));
    }

    @GetMapping("/districts/{districtId}/schools")
    @Operation(summary = "Школы района", description = "Фильтры: search, active.")
    public ResponseEntity<Page<SchoolDto>> schools(@PathVariable Long districtId,
                                                   @RequestParam(required = false) String search,
                                                   @RequestParam(required = false) Boolean active,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(schoolService.adminSchools(districtId, search, active, page, size));
    }

    @PostMapping("/schools")
    @Operation(summary = "Создать школу")
    public ResponseEntity<SchoolDto> createSchool(@Valid @RequestBody SchoolRequest request) {
        return ResponseEntity.ok(schoolService.saveSchool(null, request));
    }

    @PostMapping("/schools/bulk")
    @Operation(summary = "Массово добавить школы района", description = "Возвращает `{ \"created\": N }`; существующие названия пропускаются.")
    public ResponseEntity<Map<String, Integer>> bulkSchools(@Valid @RequestBody BulkSchoolsRequest request) {
        return ResponseEntity.ok(Map.of("created", schoolService.bulkCreateSchools(request)));
    }

    @PutMapping("/schools/{id}")
    @Operation(summary = "Изменить школу", description = "`active=false` — скрыть.")
    public ResponseEntity<SchoolDto> updateSchool(@PathVariable Long id, @Valid @RequestBody SchoolRequest request) {
        return ResponseEntity.ok(schoolService.saveSchool(id, request));
    }
}
