package com.socialsea.repository;

import com.socialsea.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @EntityGraph(attributePaths = {"sender", "receiver"})
    List<ChatMessage> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(Long senderId, Long receiverId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "receiver"})
    List<ChatMessage> findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByCreatedAtAsc(
            Long senderId,
            Long receiverId,
            Long senderIdReverse,
            Long receiverIdReverse
    );

    @EntityGraph(attributePaths = {"sender", "receiver"})
    List<ChatMessage> findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByCreatedAtDesc(
            Long senderId,
            Long receiverId,
            Long senderIdReverse,
            Long receiverIdReverse,
            Pageable pageable
    );

    long deleteBySenderIdAndReceiverIdOrSenderIdAndReceiverId(
            Long senderId,
            Long receiverId,
            Long senderIdReverse,
            Long receiverIdReverse
    );
}
