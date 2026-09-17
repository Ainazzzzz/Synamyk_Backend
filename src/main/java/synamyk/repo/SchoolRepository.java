package synamyk.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import synamyk.entities.School;

import java.util.List;

@Repository
public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findByDistrictIdAndActiveTrueOrderByNameAsc(Long districtId);

    @Query(value = "SELECT s FROM School s WHERE s.district.id = :districtId"
            + " AND (:active IS NULL OR s.active = :active)"
            + " AND (:search = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))"
            + "      OR LOWER(COALESCE(s.nameKy, '')) LIKE LOWER(CONCAT('%', :search, '%')))"
            + " ORDER BY s.name ASC",
            countQuery = "SELECT COUNT(s) FROM School s WHERE s.district.id = :districtId"
            + " AND (:active IS NULL OR s.active = :active)"
            + " AND (:search = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))"
            + "      OR LOWER(COALESCE(s.nameKy, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    /** {@code search} must not be null — pass "" for no filter (a null String binds as bytea in PostgreSQL). */
    Page<School> search(@Param("districtId") Long districtId,
                        @Param("search") String search,
                        @Param("active") Boolean active,
                        Pageable pageable);

    /** [schoolId, registeredStudents] for all schools of a district. */
    @Query("SELECT u.school.id, COUNT(u) FROM User u WHERE u.school.district.id = :districtId "
            + "AND u.active = true GROUP BY u.school.id")
    List<Object[]> countStudentsByDistrict(@Param("districtId") Long districtId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.school.id = :schoolId AND u.active = true")
    long countStudents(@Param("schoolId") Long schoolId);
}
