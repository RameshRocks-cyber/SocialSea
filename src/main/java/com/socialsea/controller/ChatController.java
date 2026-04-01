package com.socialsea.controller;

import com.socialsea.model.ChatMessage;
import com.socialsea.model.User;
import com.socialsea.repository.ChatMessageRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.CloudinaryService;
import com.socialsea.service.PresenceService;
import com.socialsea.util.UrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://43.205.213.14:5173"
})
@RequiredArgsConstructor
public class ChatController {

    private final UserRepository userRepo;
    private final ChatMessageRepository chatRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final CloudinaryService cloudinaryService;
    private final PresenceService presenceService;

    @GetMapping("/conversations")
    @Transactional(readOnly = true)
    public ResponseEntity<?> conversations(
            Authentication auth,
            HttpServletRequest request,
            @RequestParam(name = "limit", defaultValue = "300") int limit
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        presenceService.touch(me);

        int safeLimit = normalizeLimit(limit, 10, 1000, 300);
        Map<Long, Map<String, Object>> byOtherUser = new LinkedHashMap<>();

        List<ChatMessage> messages = chatRepo.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(
                me.getId(),
                me.getId(),
                PageRequest.of(0, safeLimit)
        );

        for (ChatMessage m : messages) {
            User sender = m.getSender();
            User receiver = m.getReceiver();
            if (sender == null || receiver == null) continue;
            Long otherId = Objects.equals(sender.getId(), me.getId()) ? receiver.getId() : sender.getId();
            User other = Objects.equals(sender.getId(), me.getId()) ? receiver : sender;
            if (otherId == null || other == null || byOtherUser.containsKey(otherId)) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(other.getId()));
            item.put("userId", other.getId());
            item.put("name", displayName(other));
            item.put("email", other.getEmail());
            String profilePicUrl = UrlUtils.toAbsoluteUrl(request, other.getProfilePic());
            item.put("profilePic", profilePicUrl);
            item.put("profilePicUrl", profilePicUrl);
            item.put("lastMessage", m.getText());
            item.put("lastAt", toInstant(m.getCreatedAt()) != null ? toInstant(m.getCreatedAt()).toString() : null);
            boolean online = presenceService.isOnline(other);
            item.put("online", online);
            LocalDateTime locationUpdatedAt = other.getLocationUpdatedAt();
            Instant messageAt = toInstant(m.getCreatedAt());
            Instant locationAt = toInstant(locationUpdatedAt);
            Instant presenceAt = presenceService.getLastSeenAt(other);
            Instant lastActiveAt = latestInstant(messageAt, locationAt, presenceAt);
            if (lastActiveAt != null) {
                item.put("lastActiveAt", lastActiveAt.toString());
            }
            if (presenceAt != null) {
                item.put("presenceUpdatedAt", presenceAt.toString());
            }
            item.put("locationUpdatedAt", locationAt != null ? locationAt.toString() : null);
            byOtherUser.put(otherId, item);
        }

        return ResponseEntity.ok(new ArrayList<>(byOtherUser.values()));
    }

    @GetMapping("/{otherUserId}/messages")
    @Transactional(readOnly = true)
    public ResponseEntity<?> messages(
            @PathVariable Long otherUserId,
            Authentication auth,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        presenceService.touch(me);

        Long safeOtherUserId = Objects.requireNonNull(otherUserId, "otherUserId");
        Optional<User> otherOpt = userRepo.findById(safeOtherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));

        int safeLimit = normalizeLimit(limit, 20, 200, 100);
        List<ChatMessage> recent = chatRepo.findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByCreatedAtDesc(
                me.getId(),
                safeOtherUserId,
                safeOtherUserId,
                me.getId(),
                PageRequest.of(0, safeLimit)
        );
        List<ChatMessage> list = new ArrayList<>(recent);
        Collections.reverse(list);

        List<Map<String, Object>> payload = list.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("senderId", m.getSender().getId());
            item.put("receiverId", m.getReceiver().getId());
            item.put("text", m.getText());
            item.put("audioUrl", m.getAudioUrl());
            item.put("mediaUrl", m.getMediaUrl());
            item.put("mediaType", m.getMediaType());
            item.put("fileName", m.getFileName());
            Instant createdAt = toInstant(m.getCreatedAt());
            item.put("createdAt", createdAt != null ? createdAt.toString() : null);
            item.put("mine", Objects.equals(m.getSender().getId(), me.getId()));
            return item;
        }).toList();

        return ResponseEntity.ok(payload);
    }

    @PostMapping("/presence")
    public ResponseEntity<?> presence(Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        presenceService.touch(me);
        Instant presenceAt = presenceService.getLastSeenAt(me);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "online", true,
                "presenceUpdatedAt", presenceAt != null ? presenceAt.toString() : Instant.now().toString()
        ));
    }

    private int normalizeLimit(int requested, int min, int max, int fallback) {
        if (requested < min || requested > max) return fallback;
        return requested;
    }

    private Instant latestInstant(Instant... values) {
        Instant latest = null;
        for (Instant value : values) {
            if (value == null) continue;
            if (latest == null || value.isAfter(latest)) {
                latest = value;
            }
        }
        return latest;
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) return null;
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    @DeleteMapping("/{otherUserId}")
    @Transactional
    public ResponseEntity<?> deleteConversation(@PathVariable Long otherUserId, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Long safeOtherUserId = Objects.requireNonNull(otherUserId, "otherUserId");
        Optional<User> otherOpt = userRepo.findById(safeOtherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));

        long deleted = chatRepo.deleteBySenderIdAndReceiverIdOrSenderIdAndReceiverId(
            me.getId(),
            safeOtherUserId,
            safeOtherUserId,
            me.getId()
        );

        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @PostMapping("/{otherUserId}/send")
    public ResponseEntity<?> send(
            @PathVariable Long otherUserId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Long safeOtherUserId = Objects.requireNonNull(otherUserId, "otherUserId");
        Optional<User> otherOpt = userRepo.findById(safeOtherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));

        String text = body == null ? "" : String.valueOf(body.getOrDefault("text", "")).trim();
        if (text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message text required"));
        }
        if (text.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message too long"));
        }

        User receiver = otherOpt.get();
        ChatMessage message = new ChatMessage();
        message.setSender(me);
        message.setReceiver(receiver);
        message.setText(text);
        message.setAudioUrl(null);
        message.setMediaUrl(null);
        message.setMediaType(null);
        message.setFileName(null);
        message.setCreatedAt(LocalDateTime.now());
        ChatMessage saved = chatRepo.save(message);

        Map<String, Object> receiverPayload = Objects.requireNonNull(toChatPayload(saved, me, false), "payload");
        messagingTemplate.convertAndSend("/topic/chat/" + receiver.getId(), receiverPayload);
        String receiverEmail = receiver.getEmail();
        if (receiverEmail != null && !receiverEmail.isBlank()) {
            String encodedEmail = URLEncoder.encode(receiverEmail, StandardCharsets.UTF_8);
            messagingTemplate.convertAndSend("/topic/chat/email/" + encodedEmail, receiverPayload);
            messagingTemplate.convertAndSendToUser(
                    Objects.requireNonNull(receiverEmail),
                    "/queue/chat",
                    receiverPayload
            );
        }

        return ResponseEntity.ok(toChatPayload(saved, me, true));
    }

    @PostMapping(path = "/{otherUserId}/send-audio", consumes = {"multipart/form-data"})
    public ResponseEntity<?> sendAudio(
            @PathVariable Long otherUserId,
            @RequestPart("audio") MultipartFile audio,
            Authentication auth
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Long safeOtherUserId = Objects.requireNonNull(otherUserId, "otherUserId");
        Optional<User> otherOpt = userRepo.findById(safeOtherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        if (audio == null || audio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Audio file required"));
        }

        User receiver = otherOpt.get();
        String audioUrl = cloudinaryService.upload(audio);

        ChatMessage message = new ChatMessage();
        message.setSender(me);
        message.setReceiver(receiver);
        message.setText("[Audio]");
        message.setAudioUrl(audioUrl);
        message.setMediaUrl(null);
        message.setMediaType(null);
        message.setFileName(null);
        message.setCreatedAt(LocalDateTime.now());
        ChatMessage saved = chatRepo.save(message);

        Map<String, Object> receiverPayload = Objects.requireNonNull(toChatPayload(saved, me, false), "payload");
        messagingTemplate.convertAndSend("/topic/chat/" + receiver.getId(), receiverPayload);
        String receiverEmail = receiver.getEmail();
        if (receiverEmail != null && !receiverEmail.isBlank()) {
            String encodedEmail = URLEncoder.encode(receiverEmail, StandardCharsets.UTF_8);
            messagingTemplate.convertAndSend("/topic/chat/email/" + encodedEmail, receiverPayload);
            messagingTemplate.convertAndSendToUser(
                    Objects.requireNonNull(receiverEmail),
                    "/queue/chat",
                    receiverPayload
            );
        }

        return ResponseEntity.ok(toChatPayload(saved, me, true));
    }

    @PostMapping(path = "/{otherUserId}/send-media", consumes = {"multipart/form-data"})
    public ResponseEntity<?> sendMedia(
            @PathVariable Long otherUserId,
            @RequestPart("file") MultipartFile file,
            Authentication auth
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Long safeOtherUserId = Objects.requireNonNull(otherUserId, "otherUserId");
        Optional<User> otherOpt = userRepo.findById(safeOtherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File required"));
        }

        User receiver = otherOpt.get();
        String mediaUrl = cloudinaryService.upload(file);
        String mediaType = detectMediaType(file.getContentType());
        String label = switch (mediaType) {
            case "image" -> "[Image]";
            case "video" -> "[Video]";
            case "audio" -> "[Audio]";
            default -> "[File]";
        };
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();

        ChatMessage message = new ChatMessage();
        message.setSender(me);
        message.setReceiver(receiver);
        message.setText(label);
        message.setAudioUrl(null);
        message.setMediaUrl(mediaUrl);
        message.setMediaType(mediaType);
        message.setFileName(fileName);
        message.setCreatedAt(LocalDateTime.now());
        ChatMessage saved = chatRepo.save(message);

        Map<String, Object> receiverPayload = Objects.requireNonNull(toChatPayload(saved, me, false), "payload");
        messagingTemplate.convertAndSend("/topic/chat/" + receiver.getId(), receiverPayload);
        String receiverEmail = receiver.getEmail();
        if (receiverEmail != null && !receiverEmail.isBlank()) {
            String encodedEmail = URLEncoder.encode(receiverEmail, StandardCharsets.UTF_8);
            messagingTemplate.convertAndSend("/topic/chat/email/" + encodedEmail, receiverPayload);
            messagingTemplate.convertAndSendToUser(
                    Objects.requireNonNull(receiverEmail),
                    "/queue/chat",
                    receiverPayload
            );
        }

        return ResponseEntity.ok(toChatPayload(saved, me, true));
    }

    private String detectMediaType(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/")) return "image";
        if (ct.startsWith("video/")) return "video";
        if (ct.startsWith("audio/")) return "audio";
        return "file";
    }

    private User currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepo.findByEmail(auth.getName()).orElse(null);
    }

    private String displayName(User user) {
        String raw = user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail();
        if (raw == null || raw.isBlank()) return "User";
        return raw;
    }

    private Map<String, Object> toChatPayload(ChatMessage saved, User sender, boolean mine) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", saved.getId());
        payload.put("senderId", sender.getId());
        payload.put("receiverId", saved.getReceiver().getId());
        payload.put("senderName", displayName(sender));
        payload.put("senderEmail", sender.getEmail());
        payload.put("text", saved.getText());
        payload.put("audioUrl", saved.getAudioUrl());
        payload.put("mediaUrl", saved.getMediaUrl());
        payload.put("mediaType", saved.getMediaType());
        payload.put("fileName", saved.getFileName());
        Instant createdAt = toInstant(saved.getCreatedAt());
        payload.put("createdAt", createdAt != null ? createdAt.toString() : null);
        payload.put("mine", mine);
        return payload;
    }
}
