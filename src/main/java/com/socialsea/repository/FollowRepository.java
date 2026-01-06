package com.socialsea.repository;

import com.socialsea.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerAndFollowing(User follower, User following);

    long countByFollower(User follower);   // following count
    long countByFollowing(User following); // followers count
    List<Follow> findByFollower(User follower);
    List<Post> findByUserIn(List<User> users);
}
