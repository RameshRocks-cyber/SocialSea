package com.socialsea.repository;

import com.socialsea.model.Story;
import com.socialsea.model.StoryComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryCommentRepository extends JpaRepository<StoryComment, Long> {
    long countByStory(Story story);
    long deleteByStory(Story story);
    List<StoryComment> findByStoryOrderByCreatedAtDesc(Story story);
}
