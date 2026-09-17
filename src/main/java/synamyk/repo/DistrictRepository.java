package synamyk.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import synamyk.entities.District;

import java.util.List;

@Repository
public interface DistrictRepository extends JpaRepository<District, Long> {
    List<District> findByRegionIdAndActiveTrueOrderByNameAsc(Long regionId);
    List<District> findByRegionIdOrderByNameAsc(Long regionId);
}
