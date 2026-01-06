package com.socialsea.repository;

import com.socialsea.model.Post;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUser(User user);
    List<Post> findByUserIn(List<User> users);
}
