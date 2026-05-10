package com.socialsea.repository;

import com.socialsea.dto.ChartPointDto;
import com.socialsea.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByUser(com.socialsea.model.User user);

    List<Post> findByUserIdAndApprovedTrueOrderByCreatedAtDesc(Long userId);

    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select p from Post p where p.user.id = :userId and p.approved = true order by p.createdAt desc")
    List<Post> findApprovedByUserId(@Param("userId") Long userId);

    long countByUser(com.socialsea.model.User user);

    List<Post> findByUserIn(List<com.socialsea.model.User> users);

    List<Post> findByReelTrueOrderByCreatedAtDesc();

    List<Post> findByApprovedTrueOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user"})
    @Query("""
        select p
        from Post p
        where p.reel = false
          and p.approved = true
          and p.mediaUrl is not null
        order by p.createdAt desc
    """)
    List<Post> findApprovedFeedCandidates(Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
        select p
        from Post p
        where p.reel = false
          and p.mediaUrl is not null
        order by p.createdAt desc
    """)
    List<Post> findFeedCandidates(Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
        select p
        from Post p
        where p.reel = true
          and p.approved = true
          and p.mediaUrl is not null
        order by p.createdAt desc
    """)
    List<Post> findApprovedReelCandidates(Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("select p from Post p where p.id = :id")
    java.util.Optional<Post> findPostWithUserById(@Param("id") Long id);

    List<Post> findTop10ByMediaFingerprintOrderByCreatedAtDesc(String mediaFingerprint);

    @Query("""
        SELECT new com.socialsea.dto.ChartPointDto(
            CONCAT('', FUNCTION('DATE', p.createdAt)),
            COUNT(p.id)
        )
        FROM Post p
        GROUP BY FUNCTION('DATE', p.createdAt)
        ORDER BY FUNCTION('DATE', p.createdAt)
    """)
    List<ChartPointDto> postGrowth();

    @Query("""
        SELECT new com.socialsea.dto.ChartPointDto(
            CONCAT('', FUNCTION('DATE', p.createdAt)),
            COUNT(p.id)
        )
        FROM Post p
        WHERE p.createdAt >= :fromDate
        GROUP BY FUNCTION('DATE', p.createdAt)
        ORDER BY FUNCTION('DATE', p.createdAt)
    """)
    List<ChartPointDto> postGrowthFrom(@Param("fromDate") LocalDateTime fromDate);
}
