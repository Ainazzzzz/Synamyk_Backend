package synamyk.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import synamyk.entities.ReadingPassage;

import java.util.List;

@Repository
public interface ReadingPassageRepository extends JpaRepository<ReadingPassage, Long> {
    List<ReadingPassage> findBySubTestIdOrderByOrderIndexAsc(Long subTestId);
    List<ReadingPassage> findBySubTestIdAndActiveTrueOrderByOrderIndexAsc(Long subTestId);
}
