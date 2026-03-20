package com.socialsea.controller;

import com.socialsea.model.ChatMessage;
import com.socialsea.model.User;
import com.socialsea.repository.ChatMessageRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.ChatService;
import com.socialsea.service.CloudinaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final UserRepository userRepo;
    private final ChatMessageRepository messageRepo;
    private final CloudinaryService cloudinaryService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(
        UserRepository userRepo,
        ChatMessageRepository messageRepo,
        CloudinaryService cloudinaryService,
        ChatService chatService,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.userRepo = userRepo;
        this.messageRepo = messageRepo;
        this.cloudinaryService = cloudinaryService;
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping({"/contacts", "/list", "/conversations"})
    public ResponseEntity<?> contacts(Authentication auth) {
        User me = requireUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        List<Map<String, Object>> list = chatService.buildConversationList(me);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{otherId}/messages")
    public ResponseEntity<?> messages(@PathVariable String otherId, Authentication auth) {
        User me = requireUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User other = resolveUser(otherId);
        if (other == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }

        List<ChatMessage> thread = messageRepo.findThread(me, other);
        List<Map<String, Object>> payload = thread.stream().map(this::toMessagePayload).toList();
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{otherId}/send")
    public ResponseEntity<?> send(
        @PathVariable String otherId,
        @RequestBody Map<String, Object> body,
        Authentication auth
    ) {
        User me = requireUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User other = resolveUser(otherId);
        if (other == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }
        if (other.getId() != null && other.getId().equals(me.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot message yourself"));
        }

        String text = body != null ? String.valueOf(body.getOrDefault("text", "")).trim() : "";
        if (text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message text is required"));
        }

        ChatMessage msg = new ChatMessage();
        msg.setSender(me);
        msg.setReceiver(other);
        msg.setText(text);
        msg.setCreatedAt(LocalDateTime.now());
        ChatMessage saved = messageRepo.save(msg);

        Map<String, Object> payload = toMessagePayload(saved);
        broadcastChatMessage(payload, other);

        return ResponseEntity.ok(payload);
    }

    @PostMapping({"/{otherId}/send-media", "/{otherId}/sendMedia"})
    public ResponseEntity<?> sendMedia(
        @PathVariable String otherId,
        @RequestParam("file") MultipartFile file,
        Authentication auth
    ) {
        User me = requireUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User other = resolveUser(otherId);
        if (other == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is required"));
        }

        String url = cloudinaryService.upload(file);
        ChatMessage msg = new ChatMessage();
        msg.setSender(me);
        msg.setReceiver(other);
        msg.setMediaUrl(url);
        msg.setMediaType(detectMediaType(file));
        msg.setFileName(file.getOriginalFilename());
        msg.setText("");
        msg.setCreatedAt(LocalDateTime.now());
        ChatMessage saved = messageRepo.save(msg);

        Map<String, Object> payload = toMessagePayload(saved);
        broadcastChatMessage(payload, other);
        return ResponseEntity.ok(payload);
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<?> deleteMessage(
        @PathVariable Long id,
        @RequestParam(value = "scope", required = false) String scope,
        Authentication auth
    ) {
        return handleDelete(id, scope, auth);
    }

    @DeleteMapping("/{otherId}/messages/{id}")
    public ResponseEntity<?> deleteMessageForContact(
        @PathVariable Long id,
        @RequestParam(value = "scope", required = false) String scope,
        Authentication auth
    ) {
        return handleDelete(id, scope, auth);
    }

    @PostMapping("/messages/{id}/delete")
    public ResponseEntity<?> deleteMessagePost(@PathVariable Long id, Authentication auth) {
        return handleDelete(id, null, auth);
    }

    @DeleteMapping("/messages/{id}/delete-for-everyone")
    public ResponseEntity<?> deleteForEveryone(@PathVariable Long id, Authentication auth) {
        return handleDelete(id, "everyone", auth);
    }

    @PostMapping("/messages/{id}/delete-for-everyone")
    public ResponseEntity<?> deleteForEveryonePost(@PathVariable Long id, Authentication auth) {
        return handleDelete(id, "everyone", auth);
    }

    private ResponseEntity<?> handleDelete(Long id, String scope, Authentication auth) {
        User me = requireUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        ChatMessage msg = messageRepo.findById(id).orElse(null);
        if (msg == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Message not found"));
        }

        Long senderId = msg.getSender() != null ? msg.getSender().getId() : null;
        Long receiverId = msg.getReceiver() != null ? msg.getReceiver().getId() : null;
        Long meId = me.getId();
        if (meId == null || (!meId.equals(senderId) && !meId.equals(receiverId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        boolean deleteEveryone = scope != null && scope.equalsIgnoreCase("everyone");
        if (deleteEveryone && !meId.equals(senderId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only sender can delete for everyone"));
        }

        if (deleteEveryone) {
            msg.setDeletedForEveryone(true);
            msg.setText("This message was deleted");
            msg.setMediaUrl(null);
            msg.setMediaType(null);
            msg.setFileName(null);
            messageRepo.save(msg);
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    private User requireUser(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
        return userRepo.findByEmail(auth.getName()).orElse(null);
    }

    private User resolveUser(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String trimmed = identifier.trim();
        if (trimmed.matches("\\d+")) {
            return userRepo.findById(Long.parseLong(trimmed)).orElse(null);
        }
        return userRepo.findByEmailIgnoreCase(trimmed).orElse(null);
    }

    private String detectMediaType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) return "file";
        if (contentType.startsWith("image")) return "image";
        if (contentType.startsWith("video")) return "video";
        if (contentType.startsWith("audio")) return "audio";
        return contentType;
    }

    private Map<String, Object> toMessagePayload(ChatMessage msg) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", msg.getId());
        payload.put("text", msg.isDeletedForEveryone() ? "This message was deleted" : (msg.getText() == null ? "" : msg.getText()));
        payload.put("mediaUrl", msg.isDeletedForEveryone() ? "" : (msg.getMediaUrl() == null ? "" : msg.getMediaUrl()));
        payload.put("mediaType", msg.isDeletedForEveryone() ? "" : (msg.getMediaType() == null ? "" : msg.getMediaType()));
        payload.put("fileName", msg.isDeletedForEveryone() ? "" : (msg.getFileName() == null ? "" : msg.getFileName()));
        payload.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);
        if (msg.getSender() != null) {
            payload.put("senderId", msg.getSender().getId());
            payload.put("senderEmail", msg.getSender().getEmail());
            payload.put("senderName", msg.getSender().getEmail());
        }
        if (msg.getReceiver() != null) {
            payload.put("receiverId", msg.getReceiver().getId());
            payload.put("receiverEmail", msg.getReceiver().getEmail());
        }
        return payload;
    }

    private void broadcastChatMessage(Map<String, Object> payload, User receiver) {
        if (receiver == null) return;
        try {
            messagingTemplate.convertAndSend("/topic/chat/" + receiver.getId(), payload);
            messagingTemplate.convertAndSend("/topic/chat/email/" + receiver.getEmail(), payload);
            messagingTemplate.convertAndSendToUser(receiver.getEmail(), "/queue/chat", payload);
        } catch (Exception e) {
            // ignore delivery failures
        }
    }
}
