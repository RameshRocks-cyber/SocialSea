package com.socialsea.repository;

import com.socialsea.model.Story;
import com.socialsea.model.StoryView;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryViewRepository extends JpaRepository<StoryView, Long> {
    boolean existsByUserAndStory(User user, Story story);
    long countByStory(Story story);
    long deleteByStory(Story story);
    List<StoryView> findByStoryOrderByCreatedAtDesc(Story story);
}
