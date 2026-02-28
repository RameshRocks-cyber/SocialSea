package com.socialsea.repository;

import com.socialsea.dto.ChartPointDto;
import com.socialsea.model.AnonymousPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AnonymousPostRepository extends JpaRepository<AnonymousPost, Long> {

    List<AnonymousPost> findByApprovedTrue();

    List<AnonymousPost> findByApprovedFalse();

    List<AnonymousPost> findByApprovedFalseAndRejectedFalse();

    List<AnonymousPost> findByApprovedTrueOrderByCreatedAtDesc();

    List<AnonymousPost> findByApprovedTrueAndRejectedFalseOrderByCreatedAtDesc();

    long countByApprovedFalseAndRejectedFalse();

    long countByApprovedTrue();

    long countByRejectedTrue();

    long countByApprovedFalse();

    long countByApprovedTrueAndRejectedFalse();

    @Query("""
        SELECT new com.socialsea.dto.ChartPointDto(
            CAST(FUNCTION('DATE', a.createdAt) AS String),
            COUNT(a.id)
        )
        FROM AnonymousPost a
        WHERE a.approved = true
          AND a.rejected = false
          AND a.createdAt >= :fromDate
        GROUP BY FUNCTION('DATE', a.createdAt)
        ORDER BY FUNCTION('DATE', a.createdAt)
    """)
    List<ChartPointDto> countApprovedGroupedByDate(@Param("fromDate") LocalDateTime fromDate);
}
