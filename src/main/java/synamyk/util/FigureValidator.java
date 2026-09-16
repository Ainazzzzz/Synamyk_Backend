package synamyk.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import synamyk.exception.AppException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and (de)serializes the figure attached to a question: a coordinate plane
 * (X horizontal, Y vertical) or a free geometry drawing. The client draws it; the server
 * only guarantees the structure is well-formed. Format is documented in
 * {@code ORT_FLUTTER_CLIENT_PROMPT.md} §6.
 */
@Component
@RequiredArgsConstructor
public class FigureValidator {

    private static final Set<String> TYPES = Set.of("COORDINATE_PLANE", "GEOMETRY");
    private static final int MAX_ELEMENTS = 300;
    private static final Pattern COLOR = Pattern.compile("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");
    /** x, numbers, operators and a whitelist of function names. */
    private static final Pattern EXPRESSION = Pattern.compile(
            "^(?:[0-9x.,+\\-*/^()\\s]|sin|cos|tan|cot|sqrt|abs|log|ln|exp|pi|e)+$");

    private final ObjectMapper objectMapper;

    /** Validates the figure and returns canonical JSON; {@code null}/empty map → {@code null} (no figure). */
    public String toJson(Map<String, Object> figure) {
        if (figure == null || figure.isEmpty()) return null;
        validate(figure);
        try {
            return objectMapper.writeValueAsString(figure);
        } catch (Exception e) {
            throw invalid("не удалось сохранить JSON");
        }
    }

    /** Parses stored JSON for API responses; {@code null} when absent or unreadable. */
    public Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    void validate(Map<String, Object> f) {
        String type = str(f.get("type"));
        if (type == null || !TYPES.contains(type)) {
            throw invalid("type должен быть COORDINATE_PLANE или GEOMETRY");
        }
        if ("COORDINATE_PLANE".equals(type)) {
            double xMin = requiredNumber(f, "xMin"), xMax = requiredNumber(f, "xMax");
            double yMin = requiredNumber(f, "yMin"), yMax = requiredNumber(f, "yMax");
            if (xMin >= xMax || yMin >= yMax) throw invalid("xMin < xMax и yMin < yMax");
            Object step = f.get("gridStep");
            if (step != null && (!(step instanceof Number n) || n.doubleValue() <= 0)) {
                throw invalid("gridStep должен быть > 0");
            }
        }
        Object elementsObj = f.get("elements");
        if (elementsObj == null) return;
        if (!(elementsObj instanceof List<?> elements)) throw invalid("elements должен быть массивом");
        if (elements.size() > MAX_ELEMENTS) throw invalid("не больше " + MAX_ELEMENTS + " элементов");
        for (int i = 0; i < elements.size(); i++) {
            if (!(elements.get(i) instanceof Map<?, ?> el)) throw invalid("elements[" + i + "] должен быть объектом");
            validateElement(i, el);
        }
    }

    private void validateElement(int i, Map<?, ?> el) {
        String kind = str(el.get("kind"));
        String at = "elements[" + i + "]";
        if (kind == null) throw invalid(at + ".kind обязателен");
        switch (kind) {
            case "POINT", "TEXT" -> {
                requiredNumber(el, "x", at);
                requiredNumber(el, "y", at);
                if ("TEXT".equals(kind) && str(el.get("text")) == null) throw invalid(at + ".text обязателен");
            }
            case "SEGMENT", "LINE", "RAY", "VECTOR" -> {
                point(el.get("from"), at + ".from");
                point(el.get("to"), at + ".to");
            }
            case "POLYGON", "POLYLINE" -> {
                if (!(el.get("points") instanceof List<?> pts) || pts.size() < 2) {
                    throw invalid(at + ".points — минимум 2 точки");
                }
                for (int j = 0; j < pts.size(); j++) point(pts.get(j), at + ".points[" + j + "]");
            }
            case "CIRCLE" -> {
                point(el.get("center"), at + ".center");
                if (requiredNumber(el, "radius", at) <= 0) throw invalid(at + ".radius должен быть > 0");
            }
            case "ARC" -> {
                point(el.get("center"), at + ".center");
                if (requiredNumber(el, "radius", at) <= 0) throw invalid(at + ".radius должен быть > 0");
                requiredNumber(el, "startAngle", at);
                requiredNumber(el, "endAngle", at);
            }
            case "ANGLE" -> {
                point(el.get("vertex"), at + ".vertex");
                point(el.get("from"), at + ".from");
                point(el.get("to"), at + ".to");
            }
            case "FUNCTION" -> {
                String expr = str(el.get("expression"));
                if (expr == null || expr.length() > 200 || !EXPRESSION.matcher(expr).matches()) {
                    throw invalid(at + ".expression — допустимы x, числа, + - * / ^ ( ) и sin cos tan cot sqrt abs log ln exp pi e");
                }
                Object from = el.get("xFrom"), to = el.get("xTo");
                if (from instanceof Number a && to instanceof Number b && a.doubleValue() >= b.doubleValue()) {
                    throw invalid(at + ": xFrom < xTo");
                }
            }
            default -> throw invalid(at + ".kind неизвестен: " + kind);
        }
        for (String colorKey : List.of("color", "fill")) {
            Object c = el.get(colorKey);
            if (c != null && !(c instanceof String s && COLOR.matcher(s).matches())) {
                throw invalid(at + "." + colorKey + " — формат #RRGGBB или #AARRGGBB");
            }
        }
    }

    private static void point(Object o, String at) {
        if (!(o instanceof List<?> p) || p.size() != 2
                || !(p.get(0) instanceof Number) || !(p.get(1) instanceof Number)) {
            throw invalid(at + " — ожидается [x, y]");
        }
    }

    private static double requiredNumber(Map<?, ?> m, String key) {
        return requiredNumber(m, key, "figure");
    }

    private static double requiredNumber(Map<?, ?> m, String key, String at) {
        if (!(m.get(key) instanceof Number n)) throw invalid(at + "." + key + " — число обязательно");
        return n.doubleValue();
    }

    private static String str(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static AppException invalid(String detail) {
        return new AppException("Некорректная фигура: " + detail, "Фигура туура эмес: " + detail);
    }
}
