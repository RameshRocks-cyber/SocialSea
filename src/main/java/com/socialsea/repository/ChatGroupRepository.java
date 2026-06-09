package com.socialsea.repository;

import com.socialsea.model.ChatGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {

    @EntityGraph(attributePaths = {"owner"})
    List<ChatGroup> findDistinctByMembers_UserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"owner"})
    Optional<ChatGroup> findByIdAndMembers_UserId(Long id, Long userId);
}
