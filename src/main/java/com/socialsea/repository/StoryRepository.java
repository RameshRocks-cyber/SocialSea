package com.socialsea.repository;

import com.socialsea.model.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    @Query("select s from Story s where s.expiresAt is null or s.expiresAt > :now order by s.createdAt desc")
    List<Story> findActive(@Param("now") LocalDateTime now);

    List<Story> findByUserOrderByCreatedAtDesc(com.socialsea.model.User user);
}
