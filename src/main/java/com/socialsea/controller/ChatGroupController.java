package com.socialsea.controller;

import com.socialsea.model.ChatGroup;
import com.socialsea.model.ChatGroupMember;
import com.socialsea.model.ChatMessage;
import com.socialsea.model.User;
import com.socialsea.repository.ChatGroupMemberRepository;
import com.socialsea.repository.ChatGroupRepository;
import com.socialsea.repository.ChatMessageRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.UploadService;
import com.socialsea.util.MediaUrlUtils;
import static com.socialsea.util.PublicUserPayloads.isPubliclyVisible;
import com.socialsea.util.UrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

@RestController
@RequestMapping("/api/chat/groups")
@CrossOrigin(origins = "${app.security.allowed-origins}")
@RequiredArgsConstructor
public class ChatGroupController {

    private static final int CLIENT_MESSAGE_ID_MAX_LENGTH = 120;
    private static final long MEDIA_DUPLICATE_WINDOW_MINUTES = 10;

    private final UserRepository userRepo;
    private final ChatMessageRepository chatRepo;
    private final ChatGroupRepository groupRepo;
    private final ChatGroupMemberRepository groupMemberRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final UploadService uploadService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> listGroups(
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        List<ChatGroup> groups = groupRepo.findDistinctByMembers_UserIdOrderByCreatedAtDesc(me.getId());
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ChatGroup group : groups) {
            List<ChatGroupMember> members = groupMemberRepo.findByGroupId(group.getId());
            ChatMessage latest = null;
            List<ChatMessage> latestMessages = chatRepo.findByGroupIdOrderByCreatedAtDesc(group.getId(), PageRequest.of(0, 1));
            if (!latestMessages.isEmpty()) {
                latest = latestMessages.get(0);
            }
            payload.add(toGroupSummary(group, latest, members, request, me));
        }
        return ResponseEntity.ok(payload);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createGroup(
            @RequestBody Map<String, Object> body,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        String name = String.valueOf(body == null ? "" : body.getOrDefault("name", "")).trim();
        if (name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Group name required"));
        }
        if (name.length() > 120) {
            return ResponseEntity.badRequest().body(Map.of("message", "Group name is too long"));
        }

        Set<Long> memberIds = new LinkedHashSet<>();
        memberIds.add(me.getId());
        memberIds.addAll(parseMemberIds(body == null ? null : body.get("memberIds")));

        List<User> members = userRepo.findAllById(memberIds).stream()
                .filter(user -> isPubliclyVisible(user))
                .toList();
        if (members.size() < 2) {
            return ResponseEntity.badRequest().body(Map.of("message", "Select at least one person"));
        }

        ChatGroup group = new ChatGroup();
        group.setName(name);
        group.setBio("");
        group.setProfilePic("");
        group.setOwner(me);
        group.setCreatedAt(LocalDateTime.now());
        ChatGroup savedGroup = groupRepo.save(group);

        Set<Long> added = new HashSet<>();
        for (User user : members) {
            if (user == null || user.getId() == null || !added.add(user.getId())) {
                continue;
            }
            ChatGroupMember membership = new ChatGroupMember();
            membership.setGroup(savedGroup);
            membership.setUser(user);
            membership.setJoinedAt(LocalDateTime.now());
            membership.setLastReadAt(Objects.equals(user.getId(), me.getId()) ? LocalDateTime.now() : null);
            groupMemberRepo.save(membership);
        }

        List<ChatGroupMember> groupMembers = groupMemberRepo.findByGroupId(savedGroup.getId());
        return ResponseEntity.ok(toGroupSummary(savedGroup, null, groupMembers, request, me));
    }

    @GetMapping("/{groupId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> groupDetails(
            @PathVariable Long groupId,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<ChatGroup> groupOpt = groupRepo.findByIdAndMembers_UserId(groupId, me.getId());
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Group not found"));
        }

        ChatGroup group = groupOpt.get();
        List<ChatGroupMember> members = groupMemberRepo.findByGroupId(groupId);
        List<ChatMessage> latestMessages = chatRepo.findByGroupIdOrderByCreatedAtDesc(groupId, PageRequest.of(0, 1));
        ChatMessage latest = latestMessages.isEmpty() ? null : latestMessages.get(0);
        return ResponseEntity.ok(toGroupSummary(group, latest, members, request, me));
    }

    @PatchMapping("/{groupId}")
    @Transactional
    public ResponseEntity<?> updateGroupDetails(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> body,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<ChatGroup> groupOpt = groupRepo.findByIdAndMembers_UserId(groupId, me.getId());
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Group not found"));
        }

        ChatGroup group = groupOpt.get();
        if (!isGroupAdmin(group, me)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only the group admin can edit this group"));
        }

        if (body != null && body.containsKey("name")) {
            String nextName = String.valueOf(body.get("name") == null ? "" : body.get("name")).trim();
            if (nextName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Group name required"));
            }
            if (nextName.length() > 120) {
                return ResponseEntity.badRequest().body(Map.of("message", "Group name is too long"));
            }
            group.setName(nextName);
        }

        if (body != null && body.containsKey("bio")) {
            String nextBio = String.valueOf(body.get("bio") == null ? "" : body.get("bio")).trim();
            if (nextBio.length() > 500) {
                return ResponseEntity.badRequest().body(Map.of("message", "Group bio is too long"));
            }
            group.setBio(nextBio);
        }

        ChatGroup saved = groupRepo.save(group);
        List<ChatGroupMember> members = groupMemberRepo.findByGroupId(saved.getId());
        List<ChatMessage> latestMessages = chatRepo.findByGroupIdOrderByCreatedAtDesc(saved.getId(), PageRequest.of(0, 1));
        ChatMessage latest = latestMessages.isEmpty() ? null : latestMessages.get(0);
        return ResponseEntity.ok(toGroupSummary(saved, latest, members, request, me));
    }

    @PostMapping(path = "/{groupId}/photo", consumes = {"multipart/form-data"})
    @Transactional
    public ResponseEntity<?> updateGroupPhoto(
            @PathVariable Long groupId,
            @RequestPart("photo") MultipartFile photo,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<ChatGroup> groupOpt = groupRepo.findByIdAndMembers_UserId(groupId, me.getId());
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Group not found"));
        }
        if (photo == null || photo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Photo required"));
        }

        ChatGroup group = groupOpt.get();
        if (!isGroupAdmin(group, me)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only the group admin can edit this group"));
        }

        String contentType = String.valueOf(photo.getContentType() == null ? "" : photo.getContentType()).toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only image files are allowed"));
        }

        group.setProfilePic(uploadService.upload(photo));
        ChatGroup saved = groupRepo.save(group);
        List<ChatGroupMember> members = groupMemberRepo.findByGroupId(saved.getId());
        List<ChatMessage> latestMessages = chatRepo.findByGroupIdOrderByCreatedAtDesc(saved.getId(), PageRequest.of(0, 1));
        ChatMessage latest = latestMessages.isEmpty() ? null : latestMessages.get(0);
        return ResponseEntity.ok(toGroupSummary(saved, latest, members, request, me));
    }

    @GetMapping("/{groupId}/messages")
    @Transactional(readOnly = true)
    public ResponseEntity<?> messages(
            @PathVariable Long groupId,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<ChatGroup> groupOpt = groupRepo.findByIdAndMembers_UserId(groupId, me.getId());
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Group not found"));
        }

        ChatGroup group = groupOpt.get();
        List<ChatMessage> list = chatRepo.findByGroupIdOrderByCreatedAtAsc(groupId);
        List<Map<String, Object>> payload = list.stream()
                .map(message -> toGroupMessagePayload(message, me, group, request))
                .toList();
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{groupId}/send")
    @Transactional
    public ResponseEntity<?> send(
            @PathVariable Long groupId,
            @RequestBody Map<String, String> body,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<ChatGroup> groupOpt = groupRepo.findByIdAndMembers_UserId(groupId, me.getId());
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Group not found"));
        }

        String text = body == null ? "" : String.valueOf(body.getOrDefault("text", "")).trim();
        if (text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message text required"));
        }
        if (text.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message too long"));
        }

        ChatGroup group = groupOpt.get();
        String clientMessageId = normalizeClientMessageId(body == null ? null : body.get("clientMessageId"));
        if (clientMessageId != null) {
            Optional<ChatMessage> existing = chatRepo.findTopBySenderIdAndGroupIdAndClientMessageIdOrderByCreatedAtDesc(
                    me.getId(),
                    groupId,
                    clientMessageId
            );
            if (existing.isPresent()) {
                return ResponseEntity.ok(toGroupMessagePayload(existing.get(), me, group, request));
            }
        }

        ChatMessage message = new ChatMessage();
        message.setSender(me);
        message.setReceiver(null);
        message.setGroup(group);
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
            if (clientMessageId == null) {
                throw duplicateWrite;
            }
            Optional<ChatMessage> winner = chatRepo.findTopBySenderIdAndGroupIdAndClientMessageIdOrderByCreatedAtDesc(
                    me.getId(),
                    groupId,
                    clientMessageId
            );
            if (winner.isPresent()) {
                return ResponseEntity.ok(toGroupMessagePayload(winner.get(), me, group, request));
            }
            throw duplicateWrite;
        }

        Map<String, Object> payload = toGroupMessagePayload(saved, me, group, request);
        publishGroupMessage(group, me.getId(), payload);
        return ResponseEntity.ok(payload);
    }

    @PostMapping(path = "/{groupId}/send-audio", consumes = {"multipart/form-data"})
    @Transactional
    public ResponseEntity<?> sendAudio(
            @PathVariable Long groupId,
            @RequestPart("audio") MultipartFile audio,
            @RequestPart(name = "clientMessageId", required = false) String clientMessageIdRaw,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<ChatGroup> groupOpt = groupRepo.findByIdAndMembers_UserId(groupId, me.getId());
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Group not found"));
        }
        if (audio == null || audio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Audio file required"));
        }

        ChatGroup group = groupOpt.get();
        String clientMessageId = normalizeClientMessageId(clientMessageIdRaw);
        String fileName = audio.getOriginalFilename() == null ? "" : audio.getOriginalFilename();
        long mediaSizeBytes = Math.max(0L, audio.getSize());
        String mediaFingerprint = computeMediaFingerprint(audio, "audio", fileName, mediaSizeBytes);

        if (clientMessageId != null) {
            Optional<ChatMessage> existing = chatRepo.findTopBySenderIdAndGroupIdAndClientMessageIdOrderByCreatedAtDesc(
                    me.getId(),
                    groupId,
                    clientMessageId
            );
            if (existing.isPresent()) {
                return ResponseEntity.ok(toGroupMessagePayload(existing.get(), me, group, request));
            }
        } else {
            Optional<ChatMessage> existing = findRecentGroupMediaDuplicateByFingerprint(
                    me.getId(),
                    groupId,
                    mediaFingerprint
            );
            if (existing.isPresent()) {
                return ResponseEntity.ok(toGroupMessagePayload(existing.get(), me, group, request));
            }
        }

        String audioUrl = uploadService.upload(audio);

        ChatMessage message = new ChatMessage();
        message.setSender(me);
        message.setReceiver(null);
        message.setGroup(group);
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
                Optional<ChatMessage> winner = chatRepo.findTopBySenderIdAndGroupIdAndClientMessageIdOrderByCreatedAtDesc(
                        me.getId(),
                        groupId,
                        clientMessageId
                );
                if (winner.isPresent()) {
                    return ResponseEntity.ok(toGroupMessagePayload(winner.get(), me, group, request));
                }
            }
            Optional<ChatMessage> winner = findRecentGroupMediaDuplicateByFingerprint(
                    me.getId(),
                    groupId,
                    mediaFingerprint
            );
            if (winner.isPresent()) {
                return ResponseEntity.ok(toGroupMessagePayload(winner.get(), me, group, request));
            }
            throw duplicateWrite;
        }

        Map<String, Object> payload = toGroupMessagePayload(saved, me, group, request);
        publishGroupMessage(group, me.getId(), payload);
        return ResponseEntity.ok(payload);
    }

    @PostMapping(path = "/{groupId}/send-media", consumes = {"multipart/form-data"})
    @Transactional
    public ResponseEntity<?> sendMedia(
            @PathVariable Long groupId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(name = "clientMessageId", required = false) String clientMessageIdRaw,
            Authentication auth,
            HttpServletRequest request
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<ChatGroup> groupOpt = groupRepo.findByIdAndMembers_UserId(groupId, me.getId());
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Group not found"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File required"));
        }

        ChatGroup group = groupOpt.get();
        String clientMessageId = normalizeClientMessageId(clientMessageIdRaw);
        String mediaType = detectMediaType(file.getContentType());
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        long mediaSizeBytes = Math.max(0L, file.getSize());
        String mediaFingerprint = computeMediaFingerprint(file, mediaType, fileName, mediaSizeBytes);
        String label = switch (mediaType) {
            case "image" -> "[Image]";
            case "video" -> "[Video]";
            case "audio" -> "Voice message";
            default -> fileName.isBlank() ? "[File]" : fileName;
        };

        if (clientMessageId != null) {
            Optional<ChatMessage> existing = chatRepo.findTopBySenderIdAndGroupIdAndClientMessageIdOrderByCreatedAtDesc(
                    me.getId(),
                    groupId,
                    clientMessageId
            );
            if (existing.isPresent()) {
                return ResponseEntity.ok(toGroupMessagePayload(existing.get(), me, group, request));
            }
        } else {
            Optional<ChatMessage> existingByFingerprint = findRecentGroupMediaDuplicateByFingerprint(
                    me.getId(),
                    groupId,
                    mediaFingerprint
            );
            if (existingByFingerprint.isPresent()) {
                return ResponseEntity.ok(toGroupMessagePayload(existingByFingerprint.get(), me, group, request));
            }

            Optional<ChatMessage> existingByMetadata = findRecentGroupMediaDuplicate(
                    me.getId(),
                    groupId,
                    mediaType,
                    fileName,
                    mediaSizeBytes,
                    label
            );
            if (existingByMetadata.isPresent()) {
                return ResponseEntity.ok(toGroupMessagePayload(existingByMetadata.get(), me, group, request));
            }
        }

        String mediaUrl = uploadService.upload(file);

        ChatMessage message = new ChatMessage();
        message.setSender(me);
        message.setReceiver(null);
        message.setGroup(group);
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
                Optional<ChatMessage> winner = chatRepo.findTopBySenderIdAndGroupIdAndClientMessageIdOrderByCreatedAtDesc(
                        me.getId(),
                        groupId,
                        clientMessageId
                );
                if (winner.isPresent()) {
                    return ResponseEntity.ok(toGroupMessagePayload(winner.get(), me, group, request));
                }
            }
            Optional<ChatMessage> winner = findRecentGroupMediaDuplicateByFingerprint(
                    me.getId(),
                    groupId,
                    mediaFingerprint
            );
            if (winner.isPresent()) {
                return ResponseEntity.ok(toGroupMessagePayload(winner.get(), me, group, request));
            }
            throw duplicateWrite;
        }

        Map<String, Object> payload = toGroupMessagePayload(saved, me, group, request);
        publishGroupMessage(group, me.getId(), payload);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{groupId}/mark-read")
    @Transactional
    public ResponseEntity<?> markRead(
            @PathVariable Long groupId,
            Authentication auth
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<ChatGroupMember> membershipOpt = groupMemberRepo.findByGroupIdAndUserId(groupId, me.getId());
        if (membershipOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Group not found"));
        }

        ChatGroupMember membership = membershipOpt.get();
        membership.setLastReadAt(LocalDateTime.now());
        groupMemberRepo.save(membership);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "groupId", groupId,
                "readAt", toInstant(membership.getLastReadAt()).toString()
        ));
    }

    @DeleteMapping("/{groupId}")
    @Transactional
    public ResponseEntity<?> deleteGroup(
            @PathVariable Long groupId,
            Authentication auth
    ) {
        User me = currentUser(auth);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<ChatGroup> groupOpt = groupRepo.findByIdAndMembers_UserId(groupId, me.getId());
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Group not found"));
        }

        ChatGroup group = groupOpt.get();
        List<ChatGroupMember> members = groupMemberRepo.findByGroupId(groupId);
        boolean ownerDeleting = group.getOwner() != null && Objects.equals(group.getOwner().getId(), me.getId());

        if (ownerDeleting || visibleGroupMembers(members).size() <= 2) {
            chatRepo.deleteByGroupId(groupId);
            groupRepo.delete(group);
            return ResponseEntity.ok(Map.of("deleted", true, "left", false));
        }

        Optional<ChatGroupMember> membershipOpt = groupMemberRepo.findByGroupIdAndUserId(groupId, me.getId());
        if (membershipOpt.isPresent()) {
            groupMemberRepo.delete(membershipOpt.get());
        }
        return ResponseEntity.ok(Map.of("deleted", false, "left", true));
    }

    private User currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepo.findByEmailIgnoreCase(auth.getName())
                .filter(user -> isPubliclyVisible(user))
                .orElse(null);
    }

    private Set<Long> parseMemberIds(Object raw) {
        if (!(raw instanceof Collection<?> values)) {
            return Collections.emptySet();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Object value : values) {
            String clean = String.valueOf(value == null ? "" : value).trim();
            if (!clean.matches("\\d+")) {
                continue;
            }
            ids.add(Long.parseLong(clean));
        }
        return ids;
    }

    private boolean isGroupAdmin(ChatGroup group, User me) {
        if (group == null || me == null || group.getOwner() == null || group.getOwner().getId() == null || me.getId() == null) {
            return false;
        }
        return Objects.equals(group.getOwner().getId(), me.getId());
    }

    private Map<String, Object> toGroupSummary(
            ChatGroup group,
            ChatMessage latest,
            List<ChatGroupMember> members,
            HttpServletRequest request,
            User me
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        List<ChatGroupMember> visibleMembers = visibleGroupMembers(members);
        String threadId = "group:" + group.getId();
        Instant lastAt = latest != null ? toInstant(latest.getCreatedAt()) : toInstant(group.getCreatedAt());
        String lastMessage = latest != null ? lastMessagePreview(latest) : "Group created";
        String groupProfilePic = UrlUtils.toAbsoluteUrl(request, group.getProfilePic());
        String ownerName = isPubliclyVisible(group.getOwner()) ? displayName(group.getOwner()) : "";

        item.put("id", threadId);
        item.put("threadId", threadId);
        item.put("threadType", "group");
        item.put("isGroup", true);
        item.put("groupId", group.getId());
        item.put("name", group.getName());
        item.put("bio", String.valueOf(group.getBio() == null ? "" : group.getBio()).trim());
        item.put("description", String.valueOf(group.getBio() == null ? "" : group.getBio()).trim());
        item.put("profilePic", groupProfilePic);
        item.put("profilePicUrl", groupProfilePic);
        item.put("ownerId", group.getOwner() != null ? group.getOwner().getId() : null);
        item.put("ownerName", ownerName);
        item.put("isAdmin", isGroupAdmin(group, me));
        item.put("canEdit", isGroupAdmin(group, me));
        Instant groupCreatedAt = toInstant(group.getCreatedAt());
        item.put("createdAt", groupCreatedAt != null ? groupCreatedAt.toString() : null);
        item.put("memberCount", visibleMembers.size());
        item.put("memberIds", visibleMembers.stream()
                .map(ChatGroupMember::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList());
        item.put("members", visibleMembers.stream().map(member -> {
            User user = member.getUser();
            if (user == null) {
                return Map.of();
            }
            String profilePicUrl = UrlUtils.toAbsoluteUrl(request, user.getProfilePic());
            Map<String, Object> memberPayload = new LinkedHashMap<>();
            memberPayload.put("id", user.getId());
            memberPayload.put("name", displayName(user));
            memberPayload.put("email", user.getEmail());
            memberPayload.put("profilePic", profilePicUrl);
            memberPayload.put("isAdmin", group.getOwner() != null && Objects.equals(group.getOwner().getId(), user.getId()));
            memberPayload.put("joinedAt", toInstant(member.getJoinedAt()) != null ? toInstant(member.getJoinedAt()).toString() : null);
            return memberPayload;
        }).filter(member -> !member.isEmpty()).toList());
        item.put("lastMessage", lastMessage);
        item.put("lastMessageAt", lastAt != null ? lastAt.toString() : null);
        item.put("lastAt", lastAt != null ? lastAt.toString() : null);
        return item;
    }

    private Map<String, Object> toGroupMessagePayload(
            ChatMessage message,
            User me,
            ChatGroup group,
            HttpServletRequest request
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        User sender = message.getSender();
        String senderLabel = isPubliclyVisible(sender) ? displayName(sender) : "User";
        payload.put("id", message.getId());
        payload.put("threadType", "group");
        payload.put("isGroup", true);
        payload.put("groupId", group.getId());
        payload.put("groupName", group.getName());
        payload.put("senderId", sender != null ? sender.getId() : null);
        payload.put("senderName", sender != null ? senderLabel : "User");
        payload.put("senderEmail", sender != null && isPubliclyVisible(sender) ? sender.getEmail() : null);
        payload.put("text", message.getText());
        applyMediaPayload(payload, message, request);
        payload.put("fileName", message.getFileName());
        payload.put("clientMessageId", message.getClientMessageId());
        payload.put("mediaSizeBytes", message.getMediaSizeBytes());
        payload.put("mediaFingerprint", message.getMediaFingerprint());
        Instant createdAt = toInstant(message.getCreatedAt());
        payload.put("createdAt", createdAt != null ? createdAt.toString() : null);
        payload.put("deliveredAt", toInstant(message.getDeliveredAt()) != null ? toInstant(message.getDeliveredAt()).toString() : null);
        payload.put("readAt", toInstant(message.getReadAt()) != null ? toInstant(message.getReadAt()).toString() : null);
        payload.put("delivered", message.getDeliveredAt() != null);
        payload.put("read", message.getReadAt() != null);
        payload.put("status", message.getReadAt() != null ? "READ" : message.getDeliveredAt() != null ? "DELIVERED" : "SENT");
        payload.put("mine", sender != null && Objects.equals(sender.getId(), me.getId()));
        return payload;
    }

    private void publishGroupMessage(ChatGroup group, Long excludeUserId, Map<String, Object> payload) {
        List<ChatGroupMember> members = visibleGroupMembers(groupMemberRepo.findByGroupId(group.getId()));
        for (ChatGroupMember membership : members) {
            User user = membership.getUser();
            if (user == null || user.getId() == null) {
                continue;
            }
            if (excludeUserId != null && Objects.equals(user.getId(), excludeUserId)) {
                continue;
            }
            String recipientEmail = String.valueOf(user.getEmail() == null ? "" : user.getEmail()).trim();
            if (!recipientEmail.isBlank()) {
                messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/chat", payload);
            } else {
                messagingTemplate.convertAndSend("/topic/chat/" + user.getId(), payload);
            }
        }
    }

    private String displayName(User user) {
        String raw = user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail();
        if (raw == null || raw.isBlank()) {
            return "User";
        }
        return raw;
    }

    private List<ChatGroupMember> visibleGroupMembers(List<ChatGroupMember> members) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .filter(Objects::nonNull)
                .filter(member -> isPubliclyVisible(member.getUser()))
                .toList();
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String lastMessagePreview(ChatMessage message) {
        if (message == null) {
            return null;
        }

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

    private String detectMediaType(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/")) return "image";
        if (ct.startsWith("video/")) return "video";
        if (ct.startsWith("audio/")) return "audio";
        return "file";
    }

    private Optional<ChatMessage> findRecentGroupMediaDuplicate(
            Long senderId,
            Long groupId,
            String mediaType,
            String fileName,
            long mediaSizeBytes,
            String textLabel
    ) {
        if (senderId == null || groupId == null) return Optional.empty();
        LocalDateTime after = LocalDateTime.now().minusMinutes(MEDIA_DUPLICATE_WINDOW_MINUTES);
        return chatRepo.findTopBySenderIdAndGroupIdAndMediaTypeAndFileNameAndMediaSizeBytesAndTextAndCreatedAtAfterOrderByCreatedAtDesc(
                senderId,
                groupId,
                mediaType,
                fileName == null ? "" : fileName,
                mediaSizeBytes,
                textLabel,
                after
        );
    }

    private Optional<ChatMessage> findRecentGroupMediaDuplicateByFingerprint(
            Long senderId,
            Long groupId,
            String mediaFingerprint
    ) {
        if (senderId == null || groupId == null) return Optional.empty();
        if (mediaFingerprint == null || mediaFingerprint.isBlank()) return Optional.empty();
        LocalDateTime after = LocalDateTime.now().minusMinutes(MEDIA_DUPLICATE_WINDOW_MINUTES);
        return chatRepo.findTopBySenderIdAndGroupIdAndMediaFingerprintAndCreatedAtAfterOrderByCreatedAtDesc(
                senderId,
                groupId,
                mediaFingerprint,
                after
        );
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
