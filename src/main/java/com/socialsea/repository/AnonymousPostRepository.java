package com.socialsea.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.socialsea.model.AnonymousPost;

public interface AnonymousPostRepository extends JpaRepository<AnonymousPost, Long> {
}
