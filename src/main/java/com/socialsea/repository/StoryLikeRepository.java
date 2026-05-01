package com.socialsea.repository;

import com.socialsea.model.Story;
import com.socialsea.model.StoryLike;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryLikeRepository extends JpaRepository<StoryLike, Long> {
    boolean existsByUserAndStory(User user, Story story);
    long countByStory(Story story);
    void deleteByUserAndStory(User user, Story story);
    long deleteByStory(Story story);
    List<StoryLike> findByStoryOrderByIdDesc(Story story);
}
