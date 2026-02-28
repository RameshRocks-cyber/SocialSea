package com.socialsea.repository;

import com.socialsea.model.Like;
import com.socialsea.model.Post;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserAndPost(User user, Post post);

    long countByPost(Post post);
}
