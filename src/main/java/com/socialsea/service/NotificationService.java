package com.socialsea.service;

import com.socialsea.model.Notification;
import com.socialsea.repository.NotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class NotificationService {

    private static final int MAX_RECIPIENT_LEN = 255;
    private static final int MAX_TITLE_LEN = 255;
    private static final int MAX_TYPE_LEN = 100;
    private static final int MAX_MESSAGE_LEN = 2000;

    private final NotificationRepository repo;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    public NotificationService(
            NotificationRepository repo,
            SimpMessagingTemplate messagingTemplate,
            EmailService emailService
    ) {
        this.repo = repo;
        this.messagingTemplate = messagingTemplate;
        this.emailService = emailService;
    }

    public void notifyAdmin(String title, String message, String type) {
        Notification n = new Notification();
        n.setRecipient(clip("ADMIN", MAX_RECIPIENT_LEN));
        n.setTitle(clip(title, MAX_TITLE_LEN));
        n.setType(clip(type, MAX_TYPE_LEN));
        n.setMessage(clip(message, MAX_MESSAGE_LEN));
        repo.save(n);

        try {
            messagingTemplate.convertAndSend("/topic/admin-notifications", n);
        } catch (Exception ignored) {
            // Notification persistence already succeeded.
        }

        // Never email for emergency/admin alerts unless you explicitly want that later.
    }

    /**
     * Backwards-compatible admin notification entry point.
     */
    public void notify(String title, String message, String type) {
        notifyAdmin(title, message, type);
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

    /**
     * In-app only notification.
     */
    public void notifyUser(String email, String message) {
        notifyUser(email, null, message, null);
    }

    /**
     * In-app only notification.
     * This method DOES NOT send email.
     */
    public void notifyUser(String email, String title, String message, String type) {
        saveAndSendInApp(email, title, message, type);
    }

    /**
     * Explicit method for in-app + email notifications.
     * Use this only where email is actually required.
     */
    public void notifyUserWithEmail(String email, String title, String message, String type) {
        saveAndSendInApp(email, title, message, type);

        // Hard block emergency emails from this generic method.
        if ("EMERGENCY".equalsIgnoreCase(type) || isEmergencyMessage(title) || isEmergencyMessage(message)) {
            return;
        }

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

    /**
     * Explicitly named in-app-only method for clarity in controllers.
     */
    public void notifyUserInApp(String email, String title, String message, String type) {
        saveAndSendInApp(email, title, message, type);
    }

    public long getUnreadCount() {
        return repo.countByRecipientAndReadFalse("ADMIN");
    }

    @Transactional
    public void markAllAsRead() {
        repo.markAllAsRead("ADMIN");
    }

    private void saveAndSendInApp(String email, String title, String message, String type) {
        String normalizedRecipient = normalizeRecipient(email);
        if (normalizedRecipient == null || normalizedRecipient.isBlank()) {
            return;
        }
        String safeRecipient = clip(normalizedRecipient, MAX_RECIPIENT_LEN);
        String safeRawRecipient = clip(email != null ? email.trim() : null, MAX_RECIPIENT_LEN);

        Notification n = new Notification();
        n.setRecipient(safeRecipient);
        n.setTitle(clip(title, MAX_TITLE_LEN));
        n.setType(clip(type, MAX_TYPE_LEN));
        n.setMessage(clip(message, MAX_MESSAGE_LEN));
        repo.save(n);

        try {
            messagingTemplate.convertAndSend("/topic/notifications/" + safeRecipient, n);
        } catch (Exception ignored) {
            // Notification persistence already succeeded.
        }

        try {
            messagingTemplate.convertAndSendToUser(safeRecipient, "/queue/notifications", n);
        } catch (Exception ignored) {
            // Notification persistence already succeeded.
        }

        if (safeRawRecipient != null && !safeRawRecipient.isBlank() && !safeRawRecipient.equalsIgnoreCase(safeRecipient)) {
            try {
                messagingTemplate.convertAndSend("/topic/notifications/" + safeRawRecipient, n);
            } catch (Exception ignored) {
                // Notification persistence already succeeded.
            }
            try {
                messagingTemplate.convertAndSendToUser(safeRawRecipient, "/queue/notifications", n);
            } catch (Exception ignored) {
                // Notification persistence already succeeded.
            }
        }
    }

    private String normalizeRecipient(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    private boolean isEmergencyMessage(String value) {
        if (value == null || value.isBlank()) return false;
        String v = value.toLowerCase();
        return v.contains("emergency")
                || v.contains("sos")
                || v.contains("panic")
                || v.contains("alert nearby");
    }

    private String clip(String value, int maxLen) {
        if (value == null) return null;
        String v = value.trim();
        if (v.length() <= maxLen) return v;
        return v.substring(0, maxLen);
    }
}
