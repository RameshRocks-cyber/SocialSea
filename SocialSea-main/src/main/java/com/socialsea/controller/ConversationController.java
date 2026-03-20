package com.socialsea.controller;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final UserRepository userRepo;
    private final ChatService chatService;

    public ConversationController(UserRepository userRepo, ChatService chatService) {
        this.userRepo = userRepo;
        this.chatService = chatService;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User me = userRepo.findByEmail(auth.getName()).orElse(null);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
        }
        List<Map<String, Object>> list = chatService.buildConversationList(me);
        return ResponseEntity.ok(list);
    }
}
