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
            DATE(u.createdAt), COUNT(u)
        )
        FROM User u
        GROUP BY DATE(u.createdAt)
        ORDER BY DATE(u.createdAt)
    """)
    List<ChartPointDto> userGrowth();

    @Query("""
        SELECT new com.socialsea.dto.ChartPointDto(
            CAST(u.createdAt AS date), COUNT(u)
        )
        FROM User u
        WHERE u.createdAt >= :fromDate
        GROUP BY CAST(u.createdAt AS date)
        ORDER BY CAST(u.createdAt AS date)
    """)
    List<ChartPointDto> userGrowthFrom(@Param("fromDate") LocalDateTime fromDate);
}
