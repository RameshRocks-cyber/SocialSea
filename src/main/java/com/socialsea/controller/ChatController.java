package com.socialsea.controller;

import com.socialsea.model.ChatMessage;
import com.socialsea.model.User;
import com.socialsea.repository.ChatMessageRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.CloudinaryService;
import com.socialsea.service.PresenceService;
import com.socialsea.util.MediaUrlUtils;
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
@CrossOrigin(origins = "${app.security.allowed-origins}")
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
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        safeTouch(me);

        int safeLimit = normalizeLimit(limit, 1, 200, 100);
        Map<Long, Map<String, Object>> byOtherUser = new LinkedHashMap<>();

        int scanLimit = Math.min(Math.max(200, safeLimit * 50), 5000);
        List<ChatMessage> recent = chatRepo.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(
                me.getId(),
                me.getId(),
                PageRequest.of(0, scanLimit)
        );

        for (ChatMessage m : recent) {
            if (m == null) continue;
            User sender = m.getSender();
            User receiver = m.getReceiver();
            if (sender == null || receiver == null) continue;
            User other = Objects.equals(sender.getId(), me.getId()) ? receiver : sender;
            if (other == null || other.getId() == null) continue;
            Long otherId = other.getId();
            if (byOtherUser.containsKey(otherId)) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(other.getId()));
            item.put("userId", other.getId());
            item.put("name", displayName(other));
            item.put("email", other.getEmail());
            String profilePicUrl = UrlUtils.toAbsoluteUrl(request, other.getProfilePic());
            item.put("profilePic", profilePicUrl);
            item.put("profilePicUrl", profilePicUrl);
            String lastMessage = lastMessagePreview(m);
            if (lastMessage == null) {
                continue;
            }
            item.put("lastMessage", lastMessage);
            item.put("lastAt", toInstant(m.getCreatedAt()) != null ? toInstant(m.getCreatedAt()).toString() : null);
            boolean online = presenceService.isOnline(other);
            item.put("online", online);
            item.put("isOnline", online);
            item.put("is_online", online);
            LocalDateTime locationUpdatedAt = other.getLocationUpdatedAt();
            Instant locationAt = toInstant(locationUpdatedAt);
            Instant presenceAt = presenceService.getLastSeenAt(other);
            // Last seen must represent peer presence/activity, not latest conversation item.
            Instant lastActiveAt = presenceAt;
            if (lastActiveAt != null) {
                item.put("lastActiveAt", lastActiveAt.toString());
                item.put("last_active_at", lastActiveAt.toString());
            }
            if (presenceAt != null) {
                item.put("presenceUpdatedAt", presenceAt.toString());
                item.put("presence_updated_at", presenceAt.toString());
            }
            item.put("locationUpdatedAt", locationAt != null ? locationAt.toString() : null);
            item.put("location_updated_at", locationAt != null ? locationAt.toString() : null);
            byOtherUser.put(otherId, item);
            if (byOtherUser.size() >= safeLimit) break;
        }

        return ResponseEntity.ok(new ArrayList<>(byOtherUser.values()));
    }

    private String lastMessagePreview(ChatMessage message) {
        if (message == null) return null;

        String text = message.getText();
        if (text != null) {
            String trimmed = text.trim();
            if (trimmed.startsWith("__SS_READ_RECEIPT__:")) {
                return null;
            }
            if (trimmed.startsWith("__SS_DELETE_EVERYONE__:")) {
                return "This message was deleted";
            }
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }

        String audioUrl = message.getAudioUrl();
        if (audioUrl != null && !audioUrl.isBlank()) {
            return "Voice message";
        }

        String mediaType = message.getMediaType();
        if (mediaType != null && !mediaType.isBlank()) {
            String normalized = mediaType.trim().toLowerCase(Locale.ROOT);
            if ("image".equals(normalized)) return "[Image]";
            if ("video".equals(normalized)) return "[Video]";
            if ("audio".equals(normalized)) return "Voice message";
        }

        String mediaUrl = message.getMediaUrl();
        if (mediaUrl != null && !mediaUrl.isBlank()) {
            String fileName = message.getFileName();
            if (fileName != null && !fileName.isBlank()) return fileName.trim();
            return "[File]";
        }

        return "";
    }

    @GetMapping("/{otherUserId}/messages")
    @Transactional(readOnly = true)
    public ResponseEntity<?> messages(
            @PathVariable Long otherUserId,
            Authentication auth,
            HttpServletRequest request,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        safeTouch(me);

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
            String audioUrl = UrlUtils.toAbsoluteUrl(request, m.getAudioUrl());
            String mediaUrl = UrlUtils.toAbsoluteUrl(request, m.getMediaUrl());
            String thumbnailUrl = MediaUrlUtils.thumbnailUrl(mediaUrl, m.getMediaType());
            item.put("audioUrl", audioUrl);
            item.put("mediaUrl", mediaUrl);
            item.put("thumbnailUrl", thumbnailUrl);
            item.put("thumbnail", thumbnailUrl);
            item.put("thumbUrl", thumbnailUrl);
            item.put("posterUrl", thumbnailUrl);
            item.put("mediaType", m.getMediaType());
            item.put("fileName", m.getFileName());
            Instant createdAt = toInstant(m.getCreatedAt());
            item.put("createdAt", createdAt != null ? createdAt.toString() : null);
            item.put("mine", Objects.equals(m.getSender().getId(), me.getId()));
            applyDeliveryState(item, m);
            return item;
        }).toList();

        return ResponseEntity.ok(payload);
    }

    private ResponseEntity<?> presenceResponse(Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        safeTouch(me);
        Instant presenceAt = presenceService.getLastSeenAt(me);
        boolean online = presenceService.isOnline(me);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("online", online);
        payload.put("isOnline", online);
        payload.put("is_online", online);
        if (presenceAt != null) {
            payload.put("presenceUpdatedAt", presenceAt.toString());
            payload.put("presence_updated_at", presenceAt.toString());
        } else {
            payload.put("presenceUpdatedAt", null);
            payload.put("presence_updated_at", null);
        }
        return ResponseEntity.ok(payload);
    }

    private void safeTouch(User user) {
        try {
            presenceService.touch(user);
        } catch (Exception ignored) {
            // Presence must never break core chat APIs.
        }
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public long unreadConversationCount(Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return 0;
        safeTouch(me);
        return chatRepo.countUnreadConversationCountForReceiver(me.getId());
    }

    @GetMapping("/presence")
    public ResponseEntity<?> presenceGet(Authentication auth) {
        return presenceResponse(auth);
    }

    @PostMapping("/presence")
    public ResponseEntity<?> presencePost(Authentication auth) {
        return presenceResponse(auth);
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
            Authentication auth,
            HttpServletRequest request
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
        message.setDeliveredAt(LocalDateTime.now());
        message.setReadAt(null);
        ChatMessage saved = chatRepo.save(message);

        Map<String, Object> receiverPayload = Objects.requireNonNull(toChatPayload(saved, me, false, request), "payload");
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

        return ResponseEntity.ok(toChatPayload(saved, me, true, request));
    }

    @PostMapping(path = "/{otherUserId}/send-audio", consumes = {"multipart/form-data"})
    public ResponseEntity<?> sendAudio(
            @PathVariable Long otherUserId,
            @RequestPart("audio") MultipartFile audio,
            Authentication auth,
            HttpServletRequest request
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
        message.setDeliveredAt(LocalDateTime.now());
        message.setReadAt(null);
        ChatMessage saved = chatRepo.save(message);

        Map<String, Object> receiverPayload = Objects.requireNonNull(toChatPayload(saved, me, false, request), "payload");
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

        return ResponseEntity.ok(toChatPayload(saved, me, true, request));
    }

    @PostMapping(path = "/{otherUserId}/send-media", consumes = {"multipart/form-data"})
    public ResponseEntity<?> sendMedia(
            @PathVariable Long otherUserId,
            @RequestPart("file") MultipartFile file,
            Authentication auth,
            HttpServletRequest request
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
        message.setDeliveredAt(LocalDateTime.now());
        message.setReadAt(null);
        ChatMessage saved = chatRepo.save(message);

        Map<String, Object> receiverPayload = Objects.requireNonNull(toChatPayload(saved, me, false, request), "payload");
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

        return ResponseEntity.ok(toChatPayload(saved, me, true, request));
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

    private Map<String, Object> toChatPayload(ChatMessage saved, User sender, boolean mine, HttpServletRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", saved.getId());
        payload.put("senderId", sender.getId());
        payload.put("receiverId", saved.getReceiver().getId());
        payload.put("senderName", displayName(sender));
        payload.put("senderEmail", sender.getEmail());
        payload.put("text", saved.getText());
        String audioUrl = UrlUtils.toAbsoluteUrl(request, saved.getAudioUrl());
        String mediaUrl = UrlUtils.toAbsoluteUrl(request, saved.getMediaUrl());
        String thumbnailUrl = MediaUrlUtils.thumbnailUrl(mediaUrl, saved.getMediaType());
        payload.put("audioUrl", audioUrl);
        payload.put("mediaUrl", mediaUrl);
        payload.put("thumbnailUrl", thumbnailUrl);
        payload.put("thumbnail", thumbnailUrl);
        payload.put("thumbUrl", thumbnailUrl);
        payload.put("posterUrl", thumbnailUrl);
        payload.put("mediaType", saved.getMediaType());
        payload.put("fileName", saved.getFileName());
        Instant createdAt = toInstant(saved.getCreatedAt());
        payload.put("createdAt", createdAt != null ? createdAt.toString() : null);
        applyDeliveryState(payload, saved);
        payload.put("mine", mine);
        return payload;
    }

    private void applyDeliveryState(Map<String, Object> payload, ChatMessage message) {
        Instant deliveredAt = toInstant(message.getDeliveredAt());
        Instant readAt = toInstant(message.getReadAt());
        payload.put("deliveredAt", deliveredAt != null ? deliveredAt.toString() : null);
        payload.put("readAt", readAt != null ? readAt.toString() : null);
        payload.put("delivered", deliveredAt != null);
        payload.put("read", readAt != null);
        payload.put("status", resolveDeliveryStatus(message));
    }

    private String resolveDeliveryStatus(ChatMessage message) {
        if (message.getReadAt() != null) return "READ";
        if (message.getDeliveredAt() != null) return "DELIVERED";
        return "SENT";
    }

    private void publishReadReceipt(User sender, User reader, List<Long> messageIds, String readAtIso) {
        if (sender == null || reader == null || messageIds == null || messageIds.isEmpty()) return;

        Map<String, Object> receipt = new HashMap<>();
        receipt.put("type", "CHAT_READ_RECEIPT");
        receipt.put("readerId", reader.getId());
        receipt.put("readerName", displayName(reader));
        receipt.put("conversationUserId", reader.getId());
        receipt.put("messageIds", new ArrayList<>(messageIds));
        receipt.put("readAt", readAtIso);

        Long senderId = sender.getId();
        if (senderId != null) {
            messagingTemplate.convertAndSend("/topic/chat/read/" + senderId, receipt);
        }

        String senderEmail = sender.getEmail();
        if (senderEmail != null && !senderEmail.isBlank()) {
            String encodedEmail = URLEncoder.encode(senderEmail, StandardCharsets.UTF_8);
            messagingTemplate.convertAndSend("/topic/chat/read/email/" + encodedEmail, receipt);
            messagingTemplate.convertAndSendToUser(senderEmail, "/queue/chat/read", receipt);
        }
    }

    @PostMapping("/{otherUserId}/mark-read")
    @Transactional
    public ResponseEntity<?> markRead(@PathVariable Long otherUserId, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Long safeOtherUserId = Objects.requireNonNull(otherUserId, "otherUserId");
        Optional<User> otherOpt = userRepo.findById(safeOtherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        User other = otherOpt.get();

        List<ChatMessage> list = chatRepo.findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByCreatedAtAsc(
                safeOtherUserId, me.getId(), me.getId(), safeOtherUserId
        );

        LocalDateTime now = LocalDateTime.now();
        List<ChatMessage> changed = new ArrayList<>();
        List<Long> messageIds = new ArrayList<>();
        for (ChatMessage m : list) {
            if (!Objects.equals(m.getReceiver().getId(), me.getId())) continue;
            if (m.getReadAt() != null) continue;
            if (m.getDeliveredAt() == null) {
                m.setDeliveredAt(now);
            }
            m.setReadAt(now);
            changed.add(m);
            if (m.getId() != null) {
                messageIds.add(m.getId());
            }
        }

        if (!changed.isEmpty()) {
            chatRepo.saveAll(changed);
        }

        String readAtIso = toInstant(now).toString();
        publishReadReceipt(other, me, messageIds, readAtIso);

        Map<String, Object> response = new HashMap<>();
        response.put("updated", changed.size());
        response.put("readAt", readAtIso);
        response.put("messageIds", messageIds);
        response.put("readerId", me.getId());
        response.put("otherUserId", other.getId());
        response.put("type", "CHAT_READ_RECEIPT");
        return ResponseEntity.ok(response);
    }
}
