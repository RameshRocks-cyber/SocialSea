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

    @Autowired
    private NotificationRepository repo;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void notify(String title, String message, String type) {
        Notification n = new Notification();
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type);
        n.setRecipient("ADMIN");
        repo.save(n);

        messagingTemplate.convertAndSend("/topic/admin-notifications", n);
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
        n.setRecipient("ADMIN");
        n.setMessage(message);
        repo.save(n);

        messagingTemplate.convertAndSend("/topic/admin-notifications", n);

        emailService.send(
            "admin@socialsea.com",
            "New Report",
            message
        );
    }

    public void notifyUser(String email, String message) {
        notifyUser(email, null, message, null);
    }

    public void notifyUser(String email, String title, String message, String type) {
        Notification n = new Notification();
        n.setRecipient(email);
        n.setTitle(title);
        n.setType(type);
        n.setMessage(message);
        repo.save(n);

        messagingTemplate.convertAndSend("/topic/notifications/" + email, n);

        emailService.send(
            email,
            (title != null && !title.isBlank()) ? title : "Report Update",
            message
        );
    }

    public long getUnreadCount() {
        return repo.countByRecipientAndReadFalse("ADMIN");
    }

    @Transactional
    public void markAllAsRead() {
        repo.markAllAsRead("ADMIN");
    }
}
