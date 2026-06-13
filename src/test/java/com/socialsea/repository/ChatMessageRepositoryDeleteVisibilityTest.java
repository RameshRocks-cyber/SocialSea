package com.socialsea.repository;

import com.socialsea.model.ChatMessage;
import com.socialsea.model.Role;
import com.socialsea.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ChatMessageRepositoryDeleteVisibilityTest {

    @Autowired
    private ChatMessageRepository chatRepo;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deletingConversationForOneUserHidesOnlyTheirView() {
        User alice = persistUser("alice@example.com", "Alice");
        User bob = persistUser("bob@example.com", "Bob");

        ChatMessage aliceToBob = persistMessage(alice, bob, "hello bob", LocalDateTime.now().minusMinutes(2));
        ChatMessage bobToAlice = persistMessage(bob, alice, "hello alice", LocalDateTime.now().minusMinutes(1));

        entityManager.flush();
        entityManager.clear();

        assertThat(chatRepo.findAll()).hasSize(2);
        assertThat(chatRepo.countUnreadConversationCountForReceiver(alice.getId())).isEqualTo(1);

        LocalDateTime deletedAt = LocalDateTime.now();
        int marked = chatRepo.markSenderConversationDeleted(alice.getId(), bob.getId(), deletedAt)
                + chatRepo.markReceiverConversationDeleted(alice.getId(), bob.getId(), deletedAt);

        entityManager.flush();
        entityManager.clear();

        assertThat(marked).isEqualTo(2);
        assertThat(chatRepo.findAll()).hasSize(2);
        assertThat(chatRepo.findVisibleDirectMessagesOrderByCreatedAtAsc(alice.getId(), bob.getId())).isEmpty();
        assertThat(chatRepo.findVisibleDirectMessagesOrderByCreatedAtAsc(bob.getId(), alice.getId()))
                .extracting(ChatMessage::getText)
                .containsExactly(aliceToBob.getText(), bobToAlice.getText());
        assertThat(chatRepo.countUnreadConversationCountForReceiver(alice.getId())).isZero();

        ChatMessage storedAliceToBob = entityManager.find(ChatMessage.class, aliceToBob.getId());
        ChatMessage storedBobToAlice = entityManager.find(ChatMessage.class, bobToAlice.getId());
        assertThat(storedAliceToBob.getSenderDeletedAt()).isNotNull();
        assertThat(storedAliceToBob.getReceiverDeletedAt()).isNull();
        assertThat(storedBobToAlice.getSenderDeletedAt()).isNull();
        assertThat(storedBobToAlice.getReceiverDeletedAt()).isNotNull();
    }

    private User persistUser(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setRole(Role.USER);
        user.setPassword("secret");
        return entityManager.persistAndFlush(user);
    }

    private ChatMessage persistMessage(User sender, User receiver, String text, LocalDateTime createdAt) {
        ChatMessage message = new ChatMessage();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setText(text);
        message.setCreatedAt(createdAt);
        message.setDeliveredAt(createdAt);
        message.setReadAt(null);
        return entityManager.persist(message);
    }
}
