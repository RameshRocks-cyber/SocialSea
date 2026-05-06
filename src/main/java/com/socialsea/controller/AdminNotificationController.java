package com.socialsea.controller;

import com.socialsea.model.Notification;
import com.socialsea.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
@CrossOrigin(origins = {"https://socialsea.netlify.app", "https://socialsea.co.in", "https://www.socialsea.co.in", "http://localhost:5173", "http://127.0.0.1:5173", "http://43.205.229.211:5173"})
public class AdminNotificationController {

    private final NotificationService service;

    public AdminNotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Notification> getAll() {
        return service.getAllAdmin();
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        service.markAsRead(id);
    }

    // 🔔 Get unread count
    @GetMapping("/unread-count")
    public long unreadCount() {
        return service.getUnreadCount();
    }

    @PostMapping("/read-all")
    public void markAllRead() {
        service.markAllAsRead();
    }
}
