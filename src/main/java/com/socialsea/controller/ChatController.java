package com.socialsea.controller;

import com.socialsea.model.ChatMessage;
import com.socialsea.model.User;
import com.socialsea.repository.ChatMessageRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.PresenceService;
import com.socialsea.service.UploadService;
import com.socialsea.service.WebPushService;
import com.socialsea.util.MediaUrlUtils;
import com.socialsea.util.UrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.net.URLDecoder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "${app.security.allowed-origins}")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int CLIENT_MESSAGE_ID_MAX_LENGTH = 120;
    private static final long MEDIA_DUPLICATE_WINDOW_MINUTES = 10;

    private final UserRepository userRepo;
    private final ChatMessageRepository chatRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final UploadService uploadService;
    private final PresenceService presenceService;
    private final WebPushService webPushService;
    private final ConcurrentMap<String, ReentrantLock> mediaSendLocks = new ConcurrentHashMap<>();

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
            Instant messageAt = toInstant(m.getCreatedAt());
            String messageAtIso = messageAt != null ? messageAt.toString() : null;
            item.put("lastMessage", lastMessage);
            item.put("lastAt", messageAtIso);
            item.put("lastMessageAt", messageAtIso);
            item.put("lastMessageTime", messageAtIso);
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
            @PathVariable String otherUserId,
            Authentication auth,
            HttpServletRequest request,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        safeTouch(me);

        Optional<User> otherOpt = resolveUserByIdentifier(otherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        Long safeOtherUserId = otherOpt.get().getId();

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
            applyMediaPayload(item, m, request);
            item.put("fileName", m.getFileName());
            item.put("clientMessageId", m.getClientMessageId());
            item.put("mediaSizeBytes", m.getMediaSizeBytes());
            item.put("mediaFingerprint", m.getMediaFingerprint());
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
    public ResponseEntity<?> deleteConversation(@PathVariable String otherUserId, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Optional<User> otherOpt = resolveUserByIdentifier(otherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        Long safeOtherUserId = otherOpt.get().getId();

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
            @PathVariable String otherUserId,
            @RequestBody Map<String, String> body,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Optional<User> otherOpt = resolveUserByIdentifier(otherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        Long safeOtherUserId = otherOpt.get().getId();

        String text = body == null ? "" : String.valueOf(body.getOrDefault("text", "")).trim();
        if (text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message text required"));
        }
        if (text.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message too long"));
        }

        User receiver = otherOpt.get();
        String clientMessageId = normalizeClientMessageId(body == null ? null : body.get("clientMessageId"));
        if (clientMessageId != null) {
            Optional<ChatMessage> existing = chatRepo.findTopBySenderIdAndReceiverIdAndClientMessageIdOrderByCreatedAtDesc(
                    me.getId(),
                    safeOtherUserId,
                    clientMessageId
            );
            if (existing.isPresent()) {
                return ResponseEntity.ok(toChatPayload(existing.get(), me, true, request));
            }
        }

        ChatMessage message = new ChatMessage();
        message.setSender(me);
        message.setReceiver(receiver);
        message.setText(text);
        message.setAudioUrl(null);
        message.setMediaUrl(null);
        message.setMediaType(null);
        message.setFileName(null);
        message.setClientMessageId(clientMessageId);
        message.setMediaSizeBytes(null);
        message.setMediaFingerprint(null);
        message.setCreatedAt(LocalDateTime.now());
        message.setDeliveredAt(LocalDateTime.now());
        message.setReadAt(null);
        ChatMessage saved;
        try {
            saved = chatRepo.save(message);
        } catch (DataIntegrityViolationException duplicateWrite) {
            if (clientMessageId == null) throw duplicateWrite;
            Optional<ChatMessage> winner = chatRepo.findTopBySenderIdAndReceiverIdAndClientMessageIdOrderByCreatedAtDesc(
                    me.getId(),
                    safeOtherUserId,
                    clientMessageId
            );
            if (winner.isPresent()) {
                return ResponseEntity.ok(toChatPayload(winner.get(), me, true, request));
            }
            throw duplicateWrite;
        }

        Map<String, Object> receiverPayload = Objects.requireNonNull(toChatPayload(saved, me, false, request), "payload");
        publishChatMessage(receiver, receiverPayload);
        pushChatNotification(me, receiver, text);

        return ResponseEntity.ok(toChatPayload(saved, me, true, request));
    }

    @PostMapping(path = "/{otherUserId}/send-audio", consumes = {"multipart/form-data"})
    public ResponseEntity<?> sendAudio(
            @PathVariable String otherUserId,
            @RequestPart("audio") MultipartFile audio,
            @RequestPart(name = "clientMessageId", required = false) String clientMessageIdRaw,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Optional<User> otherOpt = resolveUserByIdentifier(otherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        Long safeOtherUserId = otherOpt.get().getId();
        if (audio == null || audio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Audio file required"));
        }

        User receiver = otherOpt.get();
        String clientMessageId = normalizeClientMessageId(clientMessageIdRaw);
        String fileName = audio.getOriginalFilename() == null ? "" : audio.getOriginalFilename();
        long mediaSizeBytes = Math.max(0L, audio.getSize());
        String mediaFingerprint = computeMediaFingerprint(audio, "audio", fileName, mediaSizeBytes);

        if (clientMessageId != null) {
            Optional<ChatMessage> existing = chatRepo.findTopBySenderIdAndReceiverIdAndClientMessageIdOrderByCreatedAtDesc(
                    me.getId(),
                    safeOtherUserId,
                    clientMessageId
            );
            if (existing.isPresent()) {
                return ResponseEntity.ok(toChatPayload(existing.get(), me, true, request));
            }
        } else {
            Optional<ChatMessage> existing = findRecentMediaDuplicateByFingerprint(
                    me.getId(),
                    safeOtherUserId,
                    mediaFingerprint
            );
            if (existing.isPresent()) {
                return ResponseEntity.ok(toChatPayload(existing.get(), me, true, request));
            }
        }

        String audioUrl = uploadService.upload(audio);

        ChatMessage message = new ChatMessage();
        message.setSender(me);
        message.setReceiver(receiver);
        message.setText("[Audio]");
        message.setAudioUrl(audioUrl);
        message.setMediaUrl(null);
        message.setMediaType(null);
        message.setFileName(null);
        message.setClientMessageId(clientMessageId);
        message.setMediaSizeBytes(mediaSizeBytes);
        message.setMediaFingerprint(mediaFingerprint);
        message.setCreatedAt(LocalDateTime.now());
        message.setDeliveredAt(LocalDateTime.now());
        message.setReadAt(null);
        ChatMessage saved;
        try {
            saved = chatRepo.save(message);
        } catch (DataIntegrityViolationException duplicateWrite) {
            if (clientMessageId != null) {
                Optional<ChatMessage> winner = chatRepo.findTopBySenderIdAndReceiverIdAndClientMessageIdOrderByCreatedAtDesc(
                        me.getId(),
                        safeOtherUserId,
                        clientMessageId
                );
                if (winner.isPresent()) {
                    return ResponseEntity.ok(toChatPayload(winner.get(), me, true, request));
                }
            }
            Optional<ChatMessage> winner = findRecentMediaDuplicateByFingerprint(
                    me.getId(),
                    safeOtherUserId,
                    mediaFingerprint
            );
            if (winner.isPresent()) {
                return ResponseEntity.ok(toChatPayload(winner.get(), me, true, request));
            }
            throw duplicateWrite;
        }

        Map<String, Object> receiverPayload = Objects.requireNonNull(toChatPayload(saved, me, false, request), "payload");
        publishChatMessage(receiver, receiverPayload);
        pushChatNotification(me, receiver, "[Audio]");

        return ResponseEntity.ok(toChatPayload(saved, me, true, request));
    }

    @PostMapping(path = "/{otherUserId}/send-media", consumes = {"multipart/form-data"})
    public ResponseEntity<?> sendMedia(
            @PathVariable String otherUserId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(name = "clientMessageId", required = false) String clientMessageIdRaw,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Optional<User> otherOpt = resolveUserByIdentifier(otherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        Long safeOtherUserId = otherOpt.get().getId();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File required"));
        }

        User receiver = otherOpt.get();
        String clientMessageId = normalizeClientMessageId(clientMessageIdRaw);
        String mediaType = detectMediaType(file.getContentType());
        String label = switch (mediaType) {
            case "image" -> "[Image]";
            case "video" -> "[Video]";
            case "audio" -> "[Audio]";
            default -> "[File]";
        };
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        long mediaSizeBytes = Math.max(0L, file.getSize());
        String mediaFingerprint = computeMediaFingerprint(file, mediaType, fileName, mediaSizeBytes);
        String lockKey = buildMediaLockKey(me.getId(), safeOtherUserId, mediaFingerprint, mediaType, fileName, mediaSizeBytes);
        ReentrantLock sendLock = acquireMediaSendLock(lockKey);
        sendLock.lock();
        try {
            if (clientMessageId != null) {
                Optional<ChatMessage> existing = chatRepo.findTopBySenderIdAndReceiverIdAndClientMessageIdOrderByCreatedAtDesc(
                        me.getId(),
                        safeOtherUserId,
                        clientMessageId
                );
                if (existing.isPresent()) {
                    log.info("Media dedupe hit by clientMessageId sender={} receiver={} clientMessageId={}", me.getId(), safeOtherUserId, clientMessageId);
                    return ResponseEntity.ok(toChatPayload(existing.get(), me, true, request));
                }
            }

            Optional<ChatMessage> existingByFingerprint = findRecentMediaDuplicateByFingerprint(
                    me.getId(),
                    safeOtherUserId,
                    mediaFingerprint
            );
            if (existingByFingerprint.isPresent()) {
                log.info("Media dedupe hit by fingerprint sender={} receiver={} mediaType={} fileName={} size={} fingerprint={}",
                        me.getId(), safeOtherUserId, mediaType, fileName, mediaSizeBytes, mediaFingerprint);
                return ResponseEntity.ok(toChatPayload(existingByFingerprint.get(), me, true, request));
            }

            Optional<ChatMessage> existingByMetadata = findRecentMediaDuplicate(
                    me.getId(),
                    safeOtherUserId,
                    mediaType,
                    fileName,
                    mediaSizeBytes,
                    label
            );
            if (existingByMetadata.isPresent()) {
                log.info("Media dedupe hit by metadata sender={} receiver={} mediaType={} fileName={} size={}",
                        me.getId(), safeOtherUserId, mediaType, fileName, mediaSizeBytes);
                return ResponseEntity.ok(toChatPayload(existingByMetadata.get(), me, true, request));
            }

            String mediaUrl = uploadService.upload(file);

            ChatMessage message = new ChatMessage();
            message.setSender(me);
            message.setReceiver(receiver);
            message.setText(label);
            message.setAudioUrl(null);
            message.setMediaUrl(mediaUrl);
            message.setMediaType(mediaType);
            message.setFileName(fileName);
            message.setClientMessageId(clientMessageId);
            message.setMediaSizeBytes(mediaSizeBytes);
            message.setMediaFingerprint(mediaFingerprint);
            message.setCreatedAt(LocalDateTime.now());
            message.setDeliveredAt(LocalDateTime.now());
            message.setReadAt(null);

            ChatMessage saved;
            try {
                saved = chatRepo.save(message);
            } catch (DataIntegrityViolationException duplicateWrite) {
                if (clientMessageId != null) {
                    Optional<ChatMessage> winner = chatRepo.findTopBySenderIdAndReceiverIdAndClientMessageIdOrderByCreatedAtDesc(
                            me.getId(),
                            safeOtherUserId,
                            clientMessageId
                    );
                    if (winner.isPresent()) {
                        log.info("Media dedupe recovered after unique constraint sender={} receiver={} clientMessageId={}",
                                me.getId(), safeOtherUserId, clientMessageId);
                        return ResponseEntity.ok(toChatPayload(winner.get(), me, true, request));
                    }
                }
                Optional<ChatMessage> winner = findRecentMediaDuplicateByFingerprint(
                        me.getId(),
                        safeOtherUserId,
                        mediaFingerprint
                );
                if (winner.isPresent()) {
                    log.info("Media dedupe recovered by fingerprint after race sender={} receiver={} fingerprint={}",
                            me.getId(), safeOtherUserId, mediaFingerprint);
                    return ResponseEntity.ok(toChatPayload(winner.get(), me, true, request));
                }
                throw duplicateWrite;
            }

            Map<String, Object> receiverPayload = Objects.requireNonNull(toChatPayload(saved, me, false, request), "payload");
            publishChatMessage(receiver, receiverPayload);
            pushChatNotification(me, receiver, label);

            return ResponseEntity.ok(toChatPayload(saved, me, true, request));
        } finally {
            releaseMediaSendLock(lockKey, sendLock);
        }
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
        return userRepo.findByEmailIgnoreCase(auth.getName()).orElse(null);
    }

    private String displayName(User user) {
        String raw = user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail();
        if (raw == null || raw.isBlank()) return "User";
        return raw;
    }

    private void pushChatNotification(User sender, User receiver, String preview) {
        if (sender == null || receiver == null) return;
        String receiverEmail = receiver.getEmail() == null ? "" : receiver.getEmail().trim();
        if (receiverEmail.isBlank()) return;
        String senderEmail = sender.getEmail() == null ? "" : sender.getEmail().trim();
        if (!senderEmail.isBlank() && senderEmail.equalsIgnoreCase(receiverEmail)) return;

        String senderName = displayName(sender);
        String body = String.valueOf(preview == null ? "" : preview).trim();
        if (body.isBlank()) body = "You have a new message";
        if (body.length() > 180) body = body.substring(0, 180);
        String title = "New message from " + senderName;

        try {
            webPushService.sendToRecipient(receiverEmail, title, body, "CHAT");
        } catch (Exception ignored) {
            // Chat delivery already happened over socket; push notification is best-effort.
        }
    }

    private Map<String, Object> toChatPayload(ChatMessage saved, User sender, boolean mine, HttpServletRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", saved.getId());
        payload.put("senderId", sender.getId());
        payload.put("receiverId", saved.getReceiver().getId());
        payload.put("senderName", displayName(sender));
        payload.put("senderEmail", sender.getEmail());
        payload.put("text", saved.getText());
        applyMediaPayload(payload, saved, request);
        payload.put("fileName", saved.getFileName());
        payload.put("clientMessageId", saved.getClientMessageId());
        payload.put("mediaSizeBytes", saved.getMediaSizeBytes());
        payload.put("mediaFingerprint", saved.getMediaFingerprint());
        Instant createdAt = toInstant(saved.getCreatedAt());
        payload.put("createdAt", createdAt != null ? createdAt.toString() : null);
        applyDeliveryState(payload, saved);
        payload.put("mine", mine);
        return payload;
    }

    private void applyMediaPayload(Map<String, Object> payload, ChatMessage message, HttpServletRequest request) {
        String audioUrl = UrlUtils.toAbsoluteUrl(request, message.getAudioUrl());
        String mediaUrl = UrlUtils.toAbsoluteUrl(request, message.getMediaUrl());
        String normalizedMediaType = resolveMediaTypeForPayload(message.getMediaType(), mediaUrl, audioUrl);
        String messageType = resolveMessageType(normalizedMediaType, mediaUrl, audioUrl);
        boolean video = "video".equals(messageType);
        boolean image = "image".equals(messageType);
        boolean audio = "audio".equals(messageType);
        String primaryUrl = isNotBlank(mediaUrl) ? mediaUrl : audioUrl;
        String thumbnailUrl = MediaUrlUtils.thumbnailUrl(mediaUrl, normalizedMediaType);

        payload.put("audioUrl", audioUrl);
        payload.put("voiceUrl", audioUrl);
        payload.put("mediaUrl", mediaUrl);
        payload.put("media", mediaUrl);
        payload.put("url", primaryUrl);
        payload.put("contentUrl", primaryUrl);
        payload.put("attachmentUrl", primaryUrl);
        payload.put("thumbnailUrl", thumbnailUrl);
        payload.put("thumbnail", thumbnailUrl);
        payload.put("thumbUrl", thumbnailUrl);
        payload.put("posterUrl", thumbnailUrl);
        payload.put("mediaType", normalizedMediaType.isBlank() ? null : normalizedMediaType);
        payload.put("type", messageType);
        payload.put("messageType", messageType);
        payload.put("hasAttachment", isNotBlank(primaryUrl));

        if (video) {
            payload.put("videoUrl", mediaUrl);
            payload.put("video", mediaUrl);
            payload.put("isVideo", true);
        }
        if (image) {
            payload.put("imageUrl", mediaUrl);
            payload.put("image", mediaUrl);
            payload.put("isImage", true);
        }
        if (audio) {
            payload.put("audio", audioUrl);
            payload.put("isAudio", true);
        }
    }

    private String resolveMediaTypeForPayload(String mediaTypeRaw, String mediaUrl, String audioUrl) {
        if (isNotBlank(mediaTypeRaw)) {
            return mediaTypeRaw.trim().toLowerCase(Locale.ROOT);
        }
        if (isNotBlank(audioUrl)) {
            return "audio";
        }
        if (!isNotBlank(mediaUrl)) {
            return "";
        }
        if (MediaUrlUtils.isLikelyVideo(mediaUrl)) {
            return "video";
        }
        return "image";
    }

    private String resolveMessageType(String mediaType, String mediaUrl, String audioUrl) {
        if (isNotBlank(audioUrl)) return "audio";
        if ("video".equals(mediaType)) return "video";
        if ("image".equals(mediaType)) return "image";
        if ("audio".equals(mediaType)) return "audio";
        if ("file".equals(mediaType)) return "file";
        if (isNotBlank(mediaUrl)) {
            return MediaUrlUtils.isLikelyVideo(mediaUrl) ? "video" : "file";
        }
        return "text";
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
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

    private void publishChatMessage(User recipient, Map<String, Object> payload) {
        if (recipient == null || payload == null) return;
        String recipientEmail = String.valueOf(recipient.getEmail() == null ? "" : recipient.getEmail()).trim();
        if (!recipientEmail.isBlank()) {
            messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/chat", payload);
            return;
        }
        Long recipientId = recipient.getId();
        if (recipientId != null) {
            messagingTemplate.convertAndSend("/topic/chat/" + recipientId, payload);
        }
    }

    private void publishChatReadReceipt(User recipient, Map<String, Object> payload) {
        if (recipient == null || payload == null) return;
        String recipientEmail = String.valueOf(recipient.getEmail() == null ? "" : recipient.getEmail()).trim();
        if (!recipientEmail.isBlank()) {
            messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/chat/read", payload);
            return;
        }
        Long recipientId = recipient.getId();
        if (recipientId != null) {
            messagingTemplate.convertAndSend("/topic/chat/read/" + recipientId, payload);
        }
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
        publishChatReadReceipt(sender, receipt);
    }

    @PostMapping("/{otherUserId}/mark-read")
    @Transactional
    public ResponseEntity<?> markRead(@PathVariable String otherUserId, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        Optional<User> otherOpt = resolveUserByIdentifier(otherUserId);
        if (otherOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        User other = otherOpt.get();
        Long safeOtherUserId = other.getId();

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

    private Optional<User> resolveUserByIdentifier(String identifier) {
        String clean = normalizeIdentifier(identifier);
        if (clean.isBlank()) return Optional.empty();
        if (clean.matches("\\d+")) {
            return userRepo.findById(Long.parseLong(clean));
        }
        return userRepo.findByEmailIgnoreCase(clean)
                .or(() -> userRepo.findByNameIgnoreCase(clean));
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) return "";
        String clean = identifier.trim();
        try {
            clean = URLDecoder.decode(clean, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Keep raw identifier when URL decoding fails.
        }

        while (!clean.isEmpty() && (clean.startsWith("[") || clean.startsWith("\"") || clean.startsWith("'"))) {
            clean = clean.substring(1).trim();
        }
        while (!clean.isEmpty() && (clean.endsWith("]") || clean.endsWith("\"") || clean.endsWith("'") || clean.endsWith(","))) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        return clean;
    }

    private Optional<ChatMessage> findRecentMediaDuplicate(
            Long senderId,
            Long receiverId,
            String mediaType,
            String fileName,
            long mediaSizeBytes,
            String textLabel
    ) {
        if (senderId == null || receiverId == null) return Optional.empty();
        LocalDateTime after = LocalDateTime.now().minusMinutes(MEDIA_DUPLICATE_WINDOW_MINUTES);
        return chatRepo.findTopBySenderIdAndReceiverIdAndMediaTypeAndFileNameAndMediaSizeBytesAndTextAndCreatedAtAfterOrderByCreatedAtDesc(
                senderId,
                receiverId,
                mediaType,
                fileName == null ? "" : fileName,
                mediaSizeBytes,
                textLabel,
                after
        );
    }

    private Optional<ChatMessage> findRecentMediaDuplicateByFingerprint(
            Long senderId,
            Long receiverId,
            String mediaFingerprint
    ) {
        if (senderId == null || receiverId == null) return Optional.empty();
        if (mediaFingerprint == null || mediaFingerprint.isBlank()) return Optional.empty();
        LocalDateTime after = LocalDateTime.now().minusMinutes(MEDIA_DUPLICATE_WINDOW_MINUTES);
        return chatRepo.findTopBySenderIdAndReceiverIdAndMediaFingerprintAndCreatedAtAfterOrderByCreatedAtDesc(
                senderId,
                receiverId,
                mediaFingerprint,
                after
        );
    }

    private String buildMediaLockKey(
            Long senderId,
            Long receiverId,
            String mediaFingerprint,
            String mediaType,
            String fileName,
            long mediaSizeBytes
    ) {
        if (mediaFingerprint != null && !mediaFingerprint.isBlank()) {
            return String.join("::",
                    String.valueOf(senderId),
                    String.valueOf(receiverId),
                    mediaFingerprint
            );
        }
        return String.join("::",
                String.valueOf(senderId),
                String.valueOf(receiverId),
                String.valueOf(mediaType == null ? "" : mediaType),
                String.valueOf(fileName == null ? "" : fileName),
                String.valueOf(mediaSizeBytes)
        );
    }

    private ReentrantLock acquireMediaSendLock(String lockKey) {
        return mediaSendLocks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
    }

    private void releaseMediaSendLock(String lockKey, ReentrantLock lock) {
        if (lock == null) return;
        try {
            lock.unlock();
        } finally {
            if (!lock.hasQueuedThreads()) {
                mediaSendLocks.remove(lockKey, lock);
            }
        }
    }

    private String computeMediaFingerprint(
            MultipartFile file,
            String mediaType,
            String fileName,
            long mediaSizeBytes
    ) {
        if (file == null || file.isEmpty()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.valueOf(mediaType == null ? "" : mediaType).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(String.valueOf(fileName == null ? "" : fileName).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(String.valueOf(mediaSizeBytes).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');

            try (InputStream stream = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException algorithmError) {
            throw new IllegalStateException("SHA-256 is not available in the runtime", algorithmError);
        } catch (Exception ioError) {
            throw new RuntimeException("Unable to fingerprint uploaded media", ioError);
        }
    }

    private String normalizeClientMessageId(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim();
        if (normalized.isBlank()) return null;
        if (normalized.length() > CLIENT_MESSAGE_ID_MAX_LENGTH) {
            normalized = normalized.substring(0, CLIENT_MESSAGE_ID_MAX_LENGTH);
        }
        return normalized;
    }
}
