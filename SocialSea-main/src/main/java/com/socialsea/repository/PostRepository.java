package com.socialsea.repository;

import com.socialsea.dto.ChartPointDto;
import com.socialsea.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByUser(com.socialsea.model.User user);

    List<Post> findByUserIn(List<com.socialsea.model.User> users);

    List<Post> findByReelTrueOrderByCreatedAtDesc();

    @Query("""
        SELECT new com.socialsea.dto.ChartPointDto(
            CAST(DATE(p.createdAt) AS String),
            COUNT(p)
        )
        FROM Post p
        GROUP BY CAST(DATE(p.createdAt) AS String)
        ORDER BY CAST(DATE(p.createdAt) AS String)
    """)
    List<ChartPointDto> postGrowth();

    @Query("""
        SELECT new com.socialsea.dto.ChartPointDto(
            CAST(DATE(p.createdAt) AS String),
            COUNT(p)
        )
        FROM Post p
        WHERE p.createdAt >= :fromDate
        GROUP BY CAST(DATE(p.createdAt) AS String)
        ORDER BY CAST(DATE(p.createdAt) AS String)
    """)
    List<ChartPointDto> postGrowthFrom(@Param("fromDate") LocalDateTime fromDate);
}
