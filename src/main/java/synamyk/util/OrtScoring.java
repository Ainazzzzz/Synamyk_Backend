package synamyk.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts raw points into an ОРТ-style scaled score.
 *
 * <p>The official ЦООМО equating tables are not public; what is public is the scale:
 * the main test totals at most 245 points over 150 questions (Математика 60, Аналогии 30,
 * Чтение 30, Грамматика 30). We therefore give each section a share of the test's
 * {@code maxScore} (explicit per-section value, or proportional to the section's points)
 * and scale linearly inside a section. With the default ОРТ layout this yields
 * Математика 1+2 = 98, every other section = 49, total = 245.
 */
public final class OrtScoring {

    private OrtScoring() {}

    /** Input row for {@link #allocate}. */
    public record Section(Long id, Integer explicitMax, int totalPoints) {}

    /**
     * Max score of each section. Explicit values are kept; the rest of {@code testMax} is split
     * among the other sections proportionally to their points (largest-remainder rounding, so
     * the shares add up exactly).
     */
    public static Map<Long, Integer> allocate(int testMax, List<Section> sections) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        int explicitSum = 0;
        List<Section> implicit = new ArrayList<>();
        for (Section s : sections) {
            if (s.explicitMax() != null) {
                result.put(s.id(), Math.max(0, s.explicitMax()));
                explicitSum += Math.max(0, s.explicitMax());
            } else {
                implicit.add(s);
            }
        }
        if (implicit.isEmpty()) return result;

        int remaining = Math.max(0, testMax - explicitSum);
        long pointsSum = implicit.stream().mapToLong(Section::totalPoints).sum();

        record Share(Long id, int floor, double fraction) {}
        List<Share> shares = new ArrayList<>();
        int floorSum = 0;
        for (Section s : implicit) {
            double exact = pointsSum > 0
                    ? (double) remaining * s.totalPoints() / pointsSum
                    : (double) remaining / implicit.size();
            int floor = (int) Math.floor(exact);
            shares.add(new Share(s.id(), floor, exact - floor));
            floorSum += floor;
        }
        int leftover = remaining - floorSum;
        shares.sort(Comparator.comparingDouble(Share::fraction).reversed());
        for (int i = 0; i < shares.size(); i++) {
            Share sh = shares.get(i);
            result.put(sh.id(), sh.floor() + (i < leftover ? 1 : 0));
        }
        return result;
    }

    /** Linear scale inside a section, rounded half-up. */
    public static int scale(int earnedPoints, int totalPoints, int sectionMax) {
        if (totalPoints <= 0 || sectionMax <= 0) return 0;
        int earned = Math.max(0, Math.min(earnedPoints, totalPoints));
        return (int) Math.round((double) earned * sectionMax / totalPoints);
    }

    public static int percent(int part, int whole) {
        return whole > 0 ? (int) Math.round(part * 100.0 / whole) : 0;
    }

    /** Integer percent with one decimal, e.g. 66.7. */
    public static double percentOneDecimal(int part, int whole) {
        return whole > 0 ? Math.round(part * 1000.0 / whole) / 10.0 : 0.0;
    }
}
