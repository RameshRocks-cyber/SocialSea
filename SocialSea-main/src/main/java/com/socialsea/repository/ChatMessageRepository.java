package com.socialsea.repository;

import com.socialsea.model.ChatMessage;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE (m.sender = :user AND m.receiver = :other)
           OR (m.sender = :other AND m.receiver = :user)
        ORDER BY m.createdAt ASC
    """)
    List<ChatMessage> findThread(@Param("user") User user, @Param("other") User other);

    List<ChatMessage> findBySenderOrReceiver(User sender, User receiver);
}
