package synamyk.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import synamyk.entities.UserAllAccess;
import synamyk.enums.ProductCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserAllAccessRepository extends JpaRepository<UserAllAccess, Long> {

    Optional<UserAllAccess> findByUserIdAndProduct(Long userId, ProductCode product);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM UserAllAccess a "
            + "WHERE a.user.id = :userId AND a.product = :product "
            + "AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    boolean existsActiveAccess(@Param("userId") Long userId,
                               @Param("product") ProductCode product,
                               @Param("now") LocalDateTime now);

    List<UserAllAccess> findByUserIdOrderByGrantedAtDesc(Long userId);

    void deleteByUserIdAndProduct(Long userId, ProductCode product);
}
