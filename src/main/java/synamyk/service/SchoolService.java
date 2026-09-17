package synamyk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import synamyk.dto.school.*;
import synamyk.entities.District;
import synamyk.entities.Region;
import synamyk.entities.School;
import synamyk.entities.User;
import synamyk.exception.AppException;
import synamyk.repo.DistrictRepository;
import synamyk.repo.RegionRepository;
import synamyk.repo.SchoolRepository;
import synamyk.repo.UserRepository;
import synamyk.util.L10n;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Districts (районы) and schools directory + the user's school. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolService {

    /** A school enters the rating once this many students have registered. */
    public static final int MIN_STUDENTS_FOR_RATING = 3;

    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    // ===== PUBLIC DIRECTORY =====

    public List<DistrictDto> districts(Long regionId, String lang) {
        return districtRepository.findByRegionIdAndActiveTrueOrderByNameAsc(regionId).stream()
                .map(d -> new DistrictDto(d.getId(), regionId, L10n.pick(d.getName(), d.getNameKy(), lang), d.getNameKy(), d.getActive()))
                .toList();
    }

    public Page<SchoolDto> schools(Long districtId, String search, int page, int size, String lang) {
        String q = search != null ? search.trim() : "";
        return schoolRepository.search(districtId, q, true, PageRequest.of(page, Math.min(Math.max(size, 1), 200)))
                .map(s -> new SchoolDto(s.getId(), districtId, L10n.pick(s.getName(), s.getNameKy(), lang), s.getNameKy(), s.getActive()));
    }

    // ===== MY SCHOOL =====

    @Transactional(readOnly = true)
    public MySchoolResponse mySchool(Long userId, String lang) {
        User user = findUser(userId);
        return user.getSchool() != null ? toMySchool(user.getSchool(), lang) : null;
    }

    @Transactional
    public MySchoolResponse setSchool(Long userId, Long schoolId, String lang) {
        User user = findUser(userId);
        School school = schoolRepository.findById(schoolId)
                .filter(School::getActive)
                .orElseThrow(() -> new AppException("Школа не найдена.", "Мектеп табылган жок."));
        user.setSchool(school);
        user.setRegion(school.getDistrict().getRegion()); // keep region consistent with the school
        userRepository.save(user);
        log.info("School set: userId={}, schoolId={}", userId, schoolId);
        return toMySchool(school, lang);
    }

    public MySchoolResponse toMySchool(School school, String lang) {
        District d = school.getDistrict();
        Region r = d.getRegion();
        long students = schoolRepository.countStudents(school.getId());
        return MySchoolResponse.builder()
                .schoolId(school.getId())
                .schoolName(L10n.pick(school.getName(), school.getNameKy(), lang))
                .districtId(d.getId())
                .districtName(L10n.pick(d.getName(), d.getNameKy(), lang))
                .regionId(r.getId())
                .regionName(L10n.pick(r.getName(), r.getNameKy(), lang))
                .studentCount(students)
                .minStudents(MIN_STUDENTS_FOR_RATING)
                .inRating(students >= MIN_STUDENTS_FOR_RATING)
                .studentsNeeded((int) Math.max(0, MIN_STUDENTS_FOR_RATING - students))
                .build();
    }

    // ===== ADMIN =====

    public List<DistrictDto> adminDistricts(Long regionId) {
        return districtRepository.findByRegionIdOrderByNameAsc(regionId).stream()
                .map(d -> new DistrictDto(d.getId(), regionId, d.getName(), d.getNameKy(), d.getActive()))
                .toList();
    }

    @Transactional
    public DistrictDto saveDistrict(Long id, DistrictRequest r) {
        Region region = regionRepository.findById(r.getRegionId())
                .orElseThrow(() -> new AppException("Регион не найден.", "Аймак табылган жок."));
        District d = id == null ? new District() : districtRepository.findById(id)
                .orElseThrow(() -> new AppException("Район не найден.", "Район табылган жок."));
        d.setRegion(region);
        d.setName(r.getName().trim());
        d.setNameKy(r.getNameKy());
        d.setActive(r.getActive() == null || r.getActive());
        d = districtRepository.save(d);
        return new DistrictDto(d.getId(), region.getId(), d.getName(), d.getNameKy(), d.getActive());
    }

    public Page<SchoolDto> adminSchools(Long districtId, String search, Boolean active, int page, int size) {
        String q = search != null ? search.trim() : "";
        return schoolRepository.search(districtId, q, active, PageRequest.of(page, Math.min(Math.max(size, 1), 200)))
                .map(s -> new SchoolDto(s.getId(), districtId, s.getName(), s.getNameKy(), s.getActive()));
    }

    @Transactional
    public SchoolDto saveSchool(Long id, SchoolRequest r) {
        District district = districtRepository.findById(r.getDistrictId())
                .orElseThrow(() -> new AppException("Район не найден.", "Район табылган жок."));
        School s = id == null ? new School() : schoolRepository.findById(id)
                .orElseThrow(() -> new AppException("Школа не найдена.", "Мектеп табылган жок."));
        s.setDistrict(district);
        s.setName(r.getName().trim());
        s.setNameKy(r.getNameKy());
        s.setActive(r.getActive() == null || r.getActive());
        s = schoolRepository.save(s);
        return new SchoolDto(s.getId(), district.getId(), s.getName(), s.getNameKy(), s.getActive());
    }

    /** Adds schools by name, skipping names that already exist in the district. Returns number created. */
    @Transactional
    public int bulkCreateSchools(BulkSchoolsRequest r) {
        District district = districtRepository.findById(r.getDistrictId())
                .orElseThrow(() -> new AppException("Район не найден.", "Район табылган жок."));
        Set<String> existing = new HashSet<>();
        schoolRepository.search(district.getId(), "", null, PageRequest.of(0, 10_000))
                .forEach(s -> existing.add(s.getName().trim().toLowerCase()));
        int created = 0;
        for (String raw : r.getNames()) {
            if (raw == null || raw.isBlank()) continue;
            String name = raw.trim();
            if (!existing.add(name.toLowerCase())) continue;
            School s = new School();
            s.setDistrict(district);
            s.setName(name);
            s.setActive(true);
            schoolRepository.save(s);
            created++;
        }
        log.info("Bulk schools: districtId={}, created={}", district.getId(), created);
        return created;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException("Пользователь не найден.", "Колдонуучу табылган жок."));
    }
}
