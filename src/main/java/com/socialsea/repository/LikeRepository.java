package com.socialsea.repository;

import com.socialsea.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Long> {
    boolean existsByUserAndPost(User user, Post post);
    long countByPost(Post post);
}
