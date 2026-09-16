package synamyk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import synamyk.dto.school.*;
import synamyk.entities.User;
import synamyk.enums.SchoolRatingSort;
import synamyk.service.SchoolRatingService;
import synamyk.service.SchoolService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Школы и рейтинг школ", description = "Справочник районов и школ, выбор своей школы, «Мектептер рейтинги», рейтинг районов и учеников.")
public class SchoolController {

    private final SchoolService schoolService;
    private final SchoolRatingService ratingService;

    @GetMapping("/regions/{regionId}/districts")
    @Operation(summary = "Районы региона (публично)")
    public ResponseEntity<List<DistrictDto>> districts(@PathVariable Long regionId,
                                                       @RequestParam(defaultValue = "RU") String lang) {
        return ResponseEntity.ok(schoolService.districts(regionId, lang));
    }

    @GetMapping("/districts/{districtId}/schools")
    @Operation(summary = "Школы района (публично)", description = "Поиск по названию: `search`.")
    public ResponseEntity<Page<SchoolDto>> schools(@PathVariable Long districtId,
                                                   @RequestParam(required = false) String search,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size,
                                                   @RequestParam(defaultValue = "RU") String lang) {
        return ResponseEntity.ok(schoolService.schools(districtId, search, page, size, lang));
    }

    @GetMapping("/profile/school")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Моя школа", description = "204 — школа не выбрана.")
    public ResponseEntity<MySchoolResponse> mySchool(@AuthenticationPrincipal User user) {
        MySchoolResponse r = schoolService.mySchool(user.getId(), user.getLanguage());
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }

    @PutMapping("/profile/school")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Выбрать / сменить школу", description = "Регион пользователя выставляется по школе.")
    public ResponseEntity<MySchoolResponse> setSchool(@Valid @RequestBody SetSchoolRequest request,
                                                      @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(schoolService.setSchool(user.getId(), request.getSchoolId(), user.getLanguage()));
    }

    @GetMapping("/rating/schools")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Рейтинг школ района",
            description = "Без `districtId` — район школы пользователя. `sort`: SCORE (Упай) | ACTIVITY (Активдүүлүк) | GROWTH (Өсүш). "
                    + "Школа попадает в рейтинг, когда в ней зарегистрировано минимум 3 ученика.")
    public ResponseEntity<SchoolRatingResponse> schoolRating(
            @RequestParam(required = false) Long districtId,
            @Parameter(description = "SCORE | ACTIVITY | GROWTH") @RequestParam(defaultValue = "SCORE") SchoolRatingSort sort,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ratingService.schoolRating(user.getId(), districtId, sort, user.getLanguage()));
    }

    @GetMapping("/rating/districts")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Рейтинг районов региона", description = "Без `regionId` — регион пользователя. Значение — средний лучший балл ОРТ учеников района.")
    public ResponseEntity<DistrictRatingResponse> districtRating(@RequestParam(required = false) Long regionId,
                                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ratingService.districtRating(user.getId(), regionId, user.getLanguage()));
    }

    @GetMapping("/rating/students")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Рейтинг учеников по лучшему баллу ОРТ",
            description = "`scope`: SCHOOL (моя школа) | DISTRICT (мой район) | REGION (мой регион) | ALL. Топ-100 + `me`.")
    public ResponseEntity<StudentRatingResponse> studentRating(@RequestParam(defaultValue = "ALL") String scope,
                                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ratingService.studentRating(user.getId(), scope));
    }
}
