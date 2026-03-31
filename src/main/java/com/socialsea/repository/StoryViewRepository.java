package com.socialsea.repository;

import com.socialsea.model.Story;
import com.socialsea.model.StoryView;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryViewRepository extends JpaRepository<StoryView, Long> {
    boolean existsByUserAndStory(User user, Story story);
    long countByStory(Story story);
}
