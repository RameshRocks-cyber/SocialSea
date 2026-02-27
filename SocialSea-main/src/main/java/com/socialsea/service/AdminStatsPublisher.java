package com.socialsea.service;

import com.socialsea.dto.AdminStatsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminStatsPublisher {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private AdminService adminService;

    public void publishStats() {
        AdminStatsDto stats = adminService.getDashboardStats();
        messagingTemplate.convertAndSend("/topic/admin-stats", stats);
    }
}