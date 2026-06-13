package com.socialsea.repository;

import com.socialsea.model.Follow;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerAndFollowing(User follower, User following);

    @Query("""
        select count(f)
        from Follow f
        where f.follower = :follower
          and f.following is not null
          and f.following.banned = false
    """)
    long countByFollower(@Param("follower") User follower);

    @Query("""
        select count(f)
        from Follow f
        where f.following = :following
          and f.follower is not null
          and f.follower.banned = false
    """)
    long countByFollowing(@Param("following") User following);

    List<Follow> findByFollower(User follower);

    List<Follow> findByFollowing(User following);

    @Query("""
        select distinct f.follower
        from Follow f
        where f.following = :following
          and f.follower is not null
          and f.follower.banned = false
    """)
    List<User> findVisibleFollowers(@Param("following") User following);

    @Query("""
        select distinct f.following
        from Follow f
        where f.follower = :follower
          and f.following is not null
          and f.following.banned = false
    """)
    List<User> findVisibleFollowing(@Param("follower") User follower);

    @Query("select f.following.id from Follow f where f.follower.id = :followerId")
    List<Long> findFollowingIdsByFollowerId(@Param("followerId") Long followerId);
}
