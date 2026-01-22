package com.socialsea.service;

import com.socialsea.model.Notification;
import com.socialsea.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository repo;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void notifyAdmin(String message) {
        Notification n = new Notification();
        n.setRecipient("ADMIN");
        n.setMessage(message);
        repo.save(n);

        messagingTemplate.convertAndSend("/topic/notifications/admin@socialsea.com", n);

        emailService.send(
            "admin@socialsea.com",
            "🚨 New Report",
            message
        );
    }

    public void notifyUser(String email, String message) {
        Notification n = new Notification();
        n.setRecipient(email);
        n.setMessage(message);
        repo.save(n);

        messagingTemplate.convertAndSend("/topic/notifications/" + email, n);

        emailService.send(
            email,
            "📢 Report Update",
            message
        );
    }
}
