package com.socialsea.repository;

import com.socialsea.model.Story;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    List<Story> findByExpiresAtAfterOrderByCreatedAtDesc(LocalDateTime now);

    List<Story> findByUserInAndExpiresAtAfterOrderByCreatedAtDesc(List<User> users, LocalDateTime now);
}
