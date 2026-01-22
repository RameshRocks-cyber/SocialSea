package com.socialsea.repository;

import com.socialsea.model.AnonymousPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnonymousPostRepository
        extends JpaRepository<AnonymousPost, Long> {

    // For public feed
    List<AnonymousPost> findByApprovedTrue();

    // For admin moderation
    List<AnonymousPost> findByApprovedFalse();

    // For ordered feed
    List<AnonymousPost> findByApprovedTrueOrderByCreatedAtDesc();
}
