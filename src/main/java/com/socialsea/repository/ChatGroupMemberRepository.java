package com.socialsea.repository;

import com.socialsea.model.ChatGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatGroupMemberRepository extends JpaRepository<ChatGroupMember, Long> {

    Optional<ChatGroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    List<ChatGroupMember> findByGroupId(Long groupId);

    List<ChatGroupMember> findByUserId(Long userId);
}
