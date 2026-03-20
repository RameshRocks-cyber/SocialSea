package com.socialsea.repository;

import com.socialsea.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(Long senderId, Long receiverId);

    List<ChatMessage> findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByCreatedAtAsc(
            Long senderId,
            Long receiverId,
            Long senderIdReverse,
            Long receiverIdReverse
    );

    long deleteBySenderIdAndReceiverIdOrSenderIdAndReceiverId(
            Long senderId,
            Long receiverId,
            Long senderIdReverse,
            Long receiverIdReverse
    );
}
