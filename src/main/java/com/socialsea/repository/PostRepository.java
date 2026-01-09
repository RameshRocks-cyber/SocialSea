package com.socialsea.repository;

import com.socialsea.model.Post;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByUser(User user);

    List<Post> findByUserIn(List<User> users);

    List<Post> findByReelTrueOrderByCreatedAtDesc();
}
