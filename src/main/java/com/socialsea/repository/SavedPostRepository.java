package com.socialsea.repository;

import com.socialsea.model.SavedPost;
import com.socialsea.model.User;
import com.socialsea.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SavedPostRepository extends JpaRepository<SavedPost, Long> {
    List<SavedPost> findByUserOrderBySavedAtDesc(User user);
    Optional<SavedPost> findByUserAndPost(User user, Post post);
    boolean existsByUserAndPost(User user, Post post);
}