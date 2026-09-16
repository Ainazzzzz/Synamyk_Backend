package synamyk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import synamyk.dto.school.DistrictRatingResponse;
import synamyk.dto.school.SchoolRatingResponse;
import synamyk.dto.school.StudentRatingResponse;
import synamyk.entities.District;
import synamyk.entities.Region;
import synamyk.entities.School;
import synamyk.entities.User;
import synamyk.enums.SchoolRatingSort;
import synamyk.exception.AppException;
import synamyk.repo.*;
import synamyk.util.L10n;

import java.time.LocalDateTime;
import java.util.*;

/** School rating inside a district, district rating inside a region, student leaderboards. */
@Service
@RequiredArgsConstructor
public class SchoolRatingService {

    private static final int ACTIVITY_DAYS = 30;
    private static final int STUDENT_LIMIT = 100;

    private final SchoolRepository schoolRepository;
    private final DistrictRepository districtRepository;
    private final RegionRepository regionRepository;
    private final TestAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final SchoolService schoolService;
    private final MinioService minioService;

    @Transactional(readOnly = true)
    public SchoolRatingResponse schoolRating(Long userId, Long districtId, SchoolRatingSort sort, String lang) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("Пользователь не найден.", "Колдонуучу табылган жок."));
        School mySchool = user.getSchool();
        if (districtId == null) {
            if (mySchool == null) {
                throw new AppException("Выберите свою школу в профиле.", "Профилде мектебиңизди тандаңыз.");
            }
            districtId = mySchool.getDistrict().getId();
        }
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new AppException("Район не найден.", "Район табылган жок."));
        SchoolRatingSort effectiveSort = sort != null ? sort : SchoolRatingSort.SCORE;

        Map<Long, Long> students = toLongMap(schoolRepository.countStudentsByDistrict(districtId));
        LocalDateTime since = LocalDateTime.now().minusDays(ACTIVITY_DAYS);
        Map<Long, Double> values = switch (effectiveSort) {
            case SCORE -> toDoubleMap(attemptRepository.schoolScores(districtId), 2);
            case ACTIVITY -> toDoubleMap(attemptRepository.schoolActivity(districtId, since), 1);
            case GROWTH -> toDoubleMap(attemptRepository.schoolGrowth(districtId, since), 1);
        };

        List<SchoolRatingResponse.Entry> ranked = new ArrayList<>();
        List<SchoolRatingResponse.Entry> gathering = new ArrayList<>();
        Long myId = mySchool != null ? mySchool.getId() : null;
        for (School s : schoolRepository.findByDistrictIdAndActiveTrueOrderByNameAsc(districtId)) {
            long count = students.getOrDefault(s.getId(), 0L);
            boolean inRating = count >= SchoolService.MIN_STUDENTS_FOR_RATING;
            SchoolRatingResponse.Entry e = SchoolRatingResponse.Entry.builder()
                    .schoolId(s.getId())
                    .name(L10n.pick(s.getName(), s.getNameKy(), lang))
                    .studentCount(count)
                    .inRating(inRating)
                    .value(inRating ? round1(values.getOrDefault(s.getId(), 0.0)) : null)
                    .isMine(s.getId().equals(myId))
                    .build();
            (inRating ? ranked : gathering).add(e);
        }
        ranked.sort(Comparator.comparingDouble((SchoolRatingResponse.Entry e) -> e.getValue()).reversed()
                .thenComparing(SchoolRatingResponse.Entry::getStudentCount, Comparator.reverseOrder()));
        assignRanks(ranked);
        gathering.sort(Comparator.comparing(SchoolRatingResponse.Entry::getIsMine).reversed()
                .thenComparing(SchoolRatingResponse.Entry::getStudentCount, Comparator.reverseOrder())
                .thenComparing(SchoolRatingResponse.Entry::getName));

        List<SchoolRatingResponse.Entry> entries = new ArrayList<>(ranked);
        entries.addAll(gathering);

        return SchoolRatingResponse.builder()
                .districtId(district.getId())
                .districtName(L10n.pick(district.getName(), district.getNameKy(), lang))
                .sort(effectiveSort.name())
                .minStudents(SchoolService.MIN_STUDENTS_FOR_RATING)
                .mySchool(mySchool != null && mySchool.getDistrict().getId().equals(districtId)
                        ? schoolService.toMySchool(mySchool, lang) : null)
                .entries(entries)
                .build();
    }

    @Transactional(readOnly = true)
    public DistrictRatingResponse districtRating(Long userId, Long regionId, String lang) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("Пользователь не найден.", "Колдонуучу табылган жок."));
        if (regionId == null) {
            if (user.getRegion() == null) {
                throw new AppException("Выберите регион в профиле.", "Профилде аймакты тандаңыз.");
            }
            regionId = user.getRegion().getId();
        }
        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new AppException("Регион не найден.", "Аймак табылган жок."));
        Long myDistrictId = user.getSchool() != null ? user.getSchool().getDistrict().getId() : null;

        Map<Long, Object[]> scores = new HashMap<>();
        for (Object[] row : attemptRepository.districtScores(regionId)) {
            scores.put(((Number) row[0]).longValue(), row);
        }
        List<DistrictRatingResponse.Entry> entries = new ArrayList<>();
        for (District d : districtRepository.findByRegionIdAndActiveTrueOrderByNameAsc(regionId)) {
            Object[] row = scores.get(d.getId());
            entries.add(DistrictRatingResponse.Entry.builder()
                    .districtId(d.getId())
                    .name(L10n.pick(d.getName(), d.getNameKy(), lang))
                    .studentsWithResults(row != null ? ((Number) row[1]).longValue() : 0L)
                    .averageScore(row != null && row[2] != null ? round1(((Number) row[2]).doubleValue()) : 0.0)
                    .isMine(d.getId().equals(myDistrictId))
                    .build());
        }
        entries.sort(Comparator.comparingDouble(DistrictRatingResponse.Entry::getAverageScore).reversed()
                .thenComparing(DistrictRatingResponse.Entry::getStudentsWithResults, Comparator.reverseOrder()));
        int rank = 0;
        Double prev = null;
        for (int i = 0; i < entries.size(); i++) {
            DistrictRatingResponse.Entry e = entries.get(i);
            if (!e.getAverageScore().equals(prev)) rank = i + 1;
            e.setRank(e.getStudentsWithResults() > 0 ? rank : null);
            prev = e.getAverageScore();
        }
        return DistrictRatingResponse.builder()
                .regionId(region.getId())
                .regionName(L10n.pick(region.getName(), region.getNameKy(), lang))
                .entries(entries)
                .build();
    }

    /** scope: SCHOOL | DISTRICT | REGION | ALL (relative to the current user's school/region). */
    @Transactional(readOnly = true)
    public StudentRatingResponse studentRating(Long userId, String scope) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("Пользователь не найден.", "Колдонуучу табылган жок."));
        String s = scope == null ? "ALL" : scope.toUpperCase();
        Long schoolId = null, districtId = null, regionId = null;
        switch (s) {
            case "SCHOOL" -> schoolId = requireSchool(user).getId();
            case "DISTRICT" -> districtId = requireSchool(user).getDistrict().getId();
            case "REGION" -> {
                if (user.getRegion() == null) {
                    throw new AppException("Выберите регион в профиле.", "Профилде аймакты тандаңыз.");
                }
                regionId = user.getRegion().getId();
            }
            case "ALL" -> { }
            default -> throw new AppException("Неизвестный scope.", "Белгисиз scope.");
        }

        List<Object[]> rows = attemptRepository.studentLeaderboard(schoolId, districtId, regionId, 1000);
        List<StudentRatingResponse.Entry> all = new ArrayList<>();
        int rank = 0;
        Integer prev = null;
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            int score = ((Number) r[5]).intValue();
            if (!Integer.valueOf(score).equals(prev)) rank = i + 1;
            prev = score;
            Long id = ((Number) r[0]).longValue();
            String first = (String) r[1], last = (String) r[2];
            all.add(StudentRatingResponse.Entry.builder()
                    .rank(rank)
                    .userId(id)
                    .fullName(((first != null ? first : "") + " " + (last != null ? last : "")).trim())
                    .avatarUrl(minioService.presign((String) r[3]))
                    .schoolName((String) r[4])
                    .score(score)
                    .isMe(id.equals(userId))
                    .build());
        }
        return StudentRatingResponse.builder()
                .scope(s)
                .entries(all.subList(0, Math.min(STUDENT_LIMIT, all.size())))
                .me(all.stream().filter(StudentRatingResponse.Entry::getIsMe).findFirst().orElse(null))
                .build();
    }

    private static School requireSchool(User user) {
        if (user.getSchool() == null) {
            throw new AppException("Выберите свою школу в профиле.", "Профилде мектебиңизди тандаңыз.");
        }
        return user.getSchool();
    }

    private static void assignRanks(List<SchoolRatingResponse.Entry> ranked) {
        int rank = 0;
        Double prev = null;
        for (int i = 0; i < ranked.size(); i++) {
            SchoolRatingResponse.Entry e = ranked.get(i);
            if (!e.getValue().equals(prev)) rank = i + 1;
            e.setRank(rank);
            prev = e.getValue();
        }
    }

    private static Map<Long, Long> toLongMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) map.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        return map;
    }

    private static Map<Long, Double> toDoubleMap(List<Object[]> rows, int valueIndex) {
        Map<Long, Double> map = new HashMap<>();
        for (Object[] r : rows) {
            if (r[valueIndex] != null) map.put(((Number) r[0]).longValue(), ((Number) r[valueIndex]).doubleValue());
        }
        return map;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
