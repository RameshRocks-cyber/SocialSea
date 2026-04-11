package com.socialsea.repository;

import com.socialsea.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select case when m.sender.id = :me then m.receiver.id else m.sender.id end as otherId,
                   max(m.createdAt) as lastAt
            from ChatMessage m
            where m.sender.id = :me or m.receiver.id = :me
            group by case when m.sender.id = :me then m.receiver.id else m.sender.id end
            order by max(m.createdAt) desc
            """)
    List<Object[]> findConversationPartners(@Param("me") Long me, Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "receiver"})
    ChatMessage findTopBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByCreatedAtDesc(
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
