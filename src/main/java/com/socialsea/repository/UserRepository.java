package com.socialsea.repository;

import com.socialsea.dto.ChartPointDto;
import com.socialsea.model.User;
import com.socialsea.util.UserIdentityUtils;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findAllByEmailIgnoreCase(String email);

    default Optional<User> findByEmail(String email) {
        return findCanonicalEmailMatch(email);
    }

    default Optional<User> findByEmailIgnoreCase(String email) {
        return findCanonicalEmailMatch(email);
    }

    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<User> findTop20ByNameContainingIgnoreCase(String name);

    @Query("""
        SELECT new com.socialsea.dto.ChartPointDto(
            CONCAT('', FUNCTION('DATE', u.createdAt)),
            COUNT(u.id)
        )
        FROM User u
        GROUP BY FUNCTION('DATE', u.createdAt)
        ORDER BY FUNCTION('DATE', u.createdAt)
    """)
    List<ChartPointDto> userGrowth();

    @Query("""
        SELECT new com.socialsea.dto.ChartPointDto(
            CONCAT('', FUNCTION('DATE', u.createdAt)),
            COUNT(u.id)
        )
        FROM User u
        WHERE u.createdAt >= :fromDate
        GROUP BY FUNCTION('DATE', u.createdAt)
        ORDER BY FUNCTION('DATE', u.createdAt)
    """)
    List<ChartPointDto> userGrowthFrom(@Param("fromDate") LocalDateTime fromDate);

    private Optional<User> findCanonicalEmailMatch(String email) {
        String normalized = UserIdentityUtils.normalizeEmail(email);
        if (normalized == null) {
            return Optional.empty();
        }
        return UserIdentityUtils.selectCanonicalUser(findAllByEmailIgnoreCase(normalized));
    }
}
