package synamyk.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import synamyk.exception.AppException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigureValidatorTest {

    private final FigureValidator validator = new FigureValidator(new ObjectMapper());

    @Test
    void coordinatePlane_withElements_roundTrips() {
        Map<String, Object> figure = Map.of(
                "type", "COORDINATE_PLANE",
                "xMin", -5, "xMax", 5, "yMin", -5, "yMax", 5, "gridStep", 1,
                "elements", List.of(
                        Map.of("kind", "POINT", "x", 1, "y", 2, "label", "A"),
                        Map.of("kind", "FUNCTION", "expression", "x^2 - 2*x + 1", "color", "#1976D2"),
                        Map.of("kind", "POLYGON", "points", List.of(List.of(0, 0), List.of(3, 0), List.of(3, 4))),
                        Map.of("kind", "CIRCLE", "center", List.of(0, 0), "radius", 2)));

        String json = validator.toJson(figure);
        assertThat(json).contains("COORDINATE_PLANE");
        assertThat(validator.fromJson(json)).containsEntry("type", "COORDINATE_PLANE");
    }

    @Test
    void geometry_angleAndSegments_ok() {
        Map<String, Object> figure = Map.of(
                "type", "GEOMETRY",
                "elements", List.of(
                        Map.of("kind", "SEGMENT", "from", List.of(0, 0), "to", List.of(4, 0), "label", "a"),
                        Map.of("kind", "ANGLE", "vertex", List.of(0, 0), "from", List.of(4, 0), "to", List.of(0, 3), "right", true)));
        assertThat(validator.toJson(figure)).isNotNull();
    }

    @Test
    void emptyFigure_isNull() {
        assertThat(validator.toJson(null)).isNull();
        assertThat(validator.toJson(Map.of())).isNull();
    }

    @Test
    void invalidRanges_rejected() {
        assertThatThrownBy(() -> validator.toJson(Map.of("type", "COORDINATE_PLANE",
                "xMin", 5, "xMax", -5, "yMin", -5, "yMax", 5)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void unknownKind_orBadPoint_rejected() {
        assertThatThrownBy(() -> validator.toJson(Map.of("type", "GEOMETRY",
                "elements", List.of(Map.of("kind", "STAR")))))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> validator.toJson(Map.of("type", "GEOMETRY",
                "elements", List.of(Map.of("kind", "SEGMENT", "from", List.of(0), "to", List.of(1, 1))))))
                .isInstanceOf(AppException.class);
    }

    @Test
    void functionExpression_injection_rejected() {
        assertThatThrownBy(() -> validator.toJson(Map.of("type", "COORDINATE_PLANE",
                "xMin", -1, "xMax", 1, "yMin", -1, "yMax", 1,
                "elements", List.of(Map.of("kind", "FUNCTION", "expression", "alert(1)")))))
                .isInstanceOf(AppException.class);
    }
}
