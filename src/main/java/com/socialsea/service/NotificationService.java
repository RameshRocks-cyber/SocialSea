package com.socialsea.service;

import com.socialsea.model.Notification;
import com.socialsea.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class NotificationService {
    private static final int MAX_TITLE_LEN = 240;
    private static final int MAX_TYPE_LEN = 64;
    private static final int MAX_RECIPIENT_LEN = 240;
    private static final int MAX_MESSAGE_LEN = 240;

    @Autowired
    private NotificationRepository repo;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void notify(String title, String message, String type) {
        Notification n = new Notification();
        n.setTitle(clip(title, MAX_TITLE_LEN));
        n.setMessage(clip(message, MAX_MESSAGE_LEN));
        n.setType(clip(type, MAX_TYPE_LEN));
        n.setRecipient(clip("ADMIN", MAX_RECIPIENT_LEN));
        repo.save(n);

        try {
            messagingTemplate.convertAndSend("/topic/admin-notifications", n);
        } catch (Exception ignored) {
            // Notification persistence already succeeded.
        }
    }

    public List<Notification> getUnread() {
        return repo.findByReadFalseOrderByCreatedAtDesc();
    }

    public List<Notification> getAllAdmin() {
        return repo.findByRecipientOrderByCreatedAtDesc("ADMIN");
    }

    public void markAsRead(Long id) {
        Notification n = repo.findById(Objects.requireNonNull(id, "id")).orElseThrow();
        n.setRead(true);
        repo.save(n);
    }

    public void notifyAdmin(String message) {
        Notification n = new Notification();
        n.setRecipient(clip("ADMIN", MAX_RECIPIENT_LEN));
        n.setMessage(clip(message, MAX_MESSAGE_LEN));
        repo.save(n);

        try {
            messagingTemplate.convertAndSend("/topic/admin-notifications", n);
        } catch (Exception ignored) {
            // Notification persistence already succeeded.
        }

        try {
            emailService.send(
                "admin@socialsea.com",
                "New Report",
                clip(message, MAX_MESSAGE_LEN)
            );
        } catch (Exception ignored) {
            // Email delivery issues must not break request flow.
        }
    }

    public void notifyAdminInApp(String message) {
        Notification n = new Notification();
        n.setRecipient(clip("ADMIN", MAX_RECIPIENT_LEN));
        n.setMessage(clip(message, MAX_MESSAGE_LEN));
        repo.save(n);

        try {
            messagingTemplate.convertAndSend("/topic/admin-notifications", n);
        } catch (Exception ignored) {
            // Notification persistence already succeeded.
        }
    }

    public void notifyUser(String email, String message) {
        notifyUser(email, null, message, null);
    }

    public void notifyUser(String email, String title, String message, String type) {
        Notification n = new Notification();
        n.setRecipient(clip(normalizeRecipient(email), MAX_RECIPIENT_LEN));
        n.setTitle(clip(title, MAX_TITLE_LEN));
        n.setType(clip(type, MAX_TYPE_LEN));
        n.setMessage(clip(message, MAX_MESSAGE_LEN));
        repo.save(n);

        try {
            messagingTemplate.convertAndSend("/topic/notifications/" + clip(normalizeRecipient(email), MAX_RECIPIENT_LEN), n);
        } catch (Exception ignored) {
            // Notification persistence already succeeded.
        }

        if (!"EMERGENCY".equalsIgnoreCase(type)) {
            try {
                emailService.send(
                    email,
                    (title != null && !title.isBlank()) ? clip(title, MAX_TITLE_LEN) : "Report Update",
                    clip(message, MAX_MESSAGE_LEN)
                );
            } catch (Exception ignored) {
                // Email delivery issues must not break request flow.
            }
        }
    }

    public void notifyUserInApp(String email, String title, String message, String type) {
        Notification n = new Notification();
        n.setRecipient(clip(normalizeRecipient(email), MAX_RECIPIENT_LEN));
        n.setTitle(clip(title, MAX_TITLE_LEN));
        n.setType(clip(type, MAX_TYPE_LEN));
        n.setMessage(clip(message, MAX_MESSAGE_LEN));
        repo.save(n);

        try {
            messagingTemplate.convertAndSend("/topic/notifications/" + clip(normalizeRecipient(email), MAX_RECIPIENT_LEN), n);
        } catch (Exception ignored) {
            // Notification persistence already succeeded.
        }
    }

    public long getUnreadCount() {
        return repo.countByRecipientAndReadFalse("ADMIN");
    }

    @Transactional
    public void markAllAsRead() {
        repo.markAllAsRead("ADMIN");
    }

    private String clip(String value, int maxLen) {
        if (value == null) return null;
        String v = value.trim();
        if (v.length() <= maxLen) return v;
        return v.substring(0, maxLen);
    }

    private String normalizeRecipient(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }
}
