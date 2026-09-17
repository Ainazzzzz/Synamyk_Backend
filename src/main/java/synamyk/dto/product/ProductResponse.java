package synamyk.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "Каталожный продукт: «Все тесты» / «Все тексты»")
public class ProductResponse {
    @Schema(description = "ALL_TESTS | ALL_TEXTS")
    private String code;
    private String title;
    private String description;
    private BigDecimal price;
    @Schema(description = "Зачёркнутая цена (акция), null если нет")
    private BigDecimal oldPrice;
    @Schema(description = "Продаётся сейчас (админ включил и цена > 0)")
    private Boolean available;
    @Schema(description = "У пользователя уже есть этот доступ")
    private Boolean owned;
    @Schema(description = "Пункты-преимущества для экрана оплаты")
    private java.util.List<String> features;
}
