package synamyk.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrtScoringTest {

    @Test
    void allocate_defaultOrtLayout_givesMath98AndOthers49() {
        // Математика 1 (30), Математика 2 (30), Аналогии (30), Чтение (30), Грамматика (30)
        Map<Long, Integer> max = OrtScoring.allocate(245, List.of(
                new OrtScoring.Section(1L, null, 30),
                new OrtScoring.Section(2L, null, 30),
                new OrtScoring.Section(3L, null, 30),
                new OrtScoring.Section(4L, null, 30),
                new OrtScoring.Section(5L, null, 30)));

        assertThat(max.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(245);
        assertThat(max.get(1L) + max.get(2L)).isEqualTo(98);
        assertThat(max.get(3L)).isEqualTo(49);
        assertThat(max.get(4L)).isEqualTo(49);
        assertThat(max.get(5L)).isEqualTo(49);
    }

    @Test
    void allocate_explicitValuesKept_restSplitByPoints() {
        Map<Long, Integer> max = OrtScoring.allocate(245, List.of(
                new OrtScoring.Section(1L, 100, 60),
                new OrtScoring.Section(2L, null, 30),
                new OrtScoring.Section(3L, null, 60)));

        assertThat(max.get(1L)).isEqualTo(100);
        assertThat(max.get(2L) + max.get(3L)).isEqualTo(145);
        assertThat(max.get(3L)).isGreaterThan(max.get(2L));
    }

    @Test
    void allocate_noPoints_splitsEqually() {
        Map<Long, Integer> max = OrtScoring.allocate(10, List.of(
                new OrtScoring.Section(1L, null, 0),
                new OrtScoring.Section(2L, null, 0),
                new OrtScoring.Section(3L, null, 0)));
        assertThat(max.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
    }

    @Test
    void scale_isLinearAndClamped() {
        assertThat(OrtScoring.scale(30, 30, 49)).isEqualTo(49);
        assertThat(OrtScoring.scale(0, 30, 49)).isZero();
        assertThat(OrtScoring.scale(15, 30, 49)).isEqualTo(25);
        assertThat(OrtScoring.scale(40, 30, 49)).isEqualTo(49);
        assertThat(OrtScoring.scale(5, 0, 49)).isZero();
    }

    @Test
    void percentOneDecimal() {
        assertThat(OrtScoring.percentOneDecimal(2, 3)).isEqualTo(66.7);
        assertThat(OrtScoring.percentOneDecimal(0, 0)).isEqualTo(0.0);
    }
}
