package com.socialsea.repository;

import com.socialsea.dto.ChartPointDto;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

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
}
