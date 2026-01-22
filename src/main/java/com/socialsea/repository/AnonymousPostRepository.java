package com.socialsea.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.socialsea.model.AnonymousPost;

import java.util.List;

public interface AnonymousPostRepository extends JpaRepository<AnonymousPost, Long> {
    List<AnonymousPost> findByApprovedTrue();
    List<AnonymousPost> findByApprovedFalse();

    List<AnonymousPost> findByApprovedTrueOrderByCreatedAtDesc();

    List<AnonymousPost> findByApprovedFalseAndRejectedFalse();

    // ✅ Public feed
    List<AnonymousPost> findByApprovedTrueAndRejectedFalseOrderByCreatedAtDesc();
}
