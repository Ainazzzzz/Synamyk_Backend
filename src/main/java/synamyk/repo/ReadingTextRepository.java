package synamyk.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import synamyk.entities.ReadingText;

import java.util.List;

@Repository
public interface ReadingTextRepository extends JpaRepository<ReadingText, Long> {
    List<ReadingText> findByActiveTrueOrderByOrderIndexAscIdAsc();
    List<ReadingText> findAllByOrderByOrderIndexAscIdAsc();
}
