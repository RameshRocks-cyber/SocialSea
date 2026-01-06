package com.socialsea.repository;

import com.socialsea.model.Post;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // ✅ ADD THIS
    List<Post> findByUserIn(List<User> users);

    // ✅ ALSO ADD THIS (used later)
    List<Post> findByUser(User user);
}
