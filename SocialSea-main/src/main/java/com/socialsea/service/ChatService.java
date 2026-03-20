package com.socialsea.service;

import com.socialsea.model.ChatMessage;
import com.socialsea.model.User;
import com.socialsea.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatService {

    private final ChatMessageRepository messageRepo;

    public ChatService(ChatMessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    public List<Map<String, Object>> buildConversationList(User me) {
        if (me == null) return List.of();
        List<ChatMessage> messages = messageRepo.findBySenderOrReceiver(me, me);
        if (messages == null || messages.isEmpty()) return List.of();

        Map<Long, ChatMessage> lastByUserId = new HashMap<>();
        Map<Long, User> userById = new HashMap<>();

        for (ChatMessage msg : messages) {
            if (msg == null) continue;
            User other = resolveOther(me, msg);
            if (other == null || other.getId() == null) continue;
            userById.putIfAbsent(other.getId(), other);
            ChatMessage prev = lastByUserId.get(other.getId());
            if (prev == null || isAfter(msg.getCreatedAt(), prev.getCreatedAt())) {
                lastByUserId.put(other.getId(), msg);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, User> entry : userById.entrySet()) {
            User other = entry.getValue();
            ChatMessage last = lastByUserId.get(entry.getKey());
            Map<String, Object> payload = new HashMap<>(toUserItem(other));
            if (last != null) {
                payload.put("lastMessage", summarizeLastMessage(last));
                payload.put("lastMessageAt", last.getCreatedAt() != null ? last.getCreatedAt().toString() : null);
                payload.put("senderId", last.getSender() != null ? last.getSender().getId() : null);
                payload.put("receiverId", last.getReceiver() != null ? last.getReceiver().getId() : null);
            }
            result.add(payload);
        }

        result.sort((a, b) -> {
            String atA = String.valueOf(a.get("lastMessageAt"));
            String atB = String.valueOf(b.get("lastMessageAt"));
            LocalDateTime timeA = parseTime(atA);
            LocalDateTime timeB = parseTime(atB);
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA);
        });

        return result;
    }

    public Map<String, Object> toUserItem(User user) {
        if (user == null) return Map.of();
        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("email", user.getEmail());
        item.put("name", user.getEmail());
        item.put("username", user.getEmail());
        item.put("profilePic", null);
        item.put("profilePicUrl", null);
        return item;
    }

    private User resolveOther(User me, ChatMessage msg) {
        if (msg == null || me == null || me.getId() == null) return null;
        User sender = msg.getSender();
        User receiver = msg.getReceiver();
        if (sender != null && me.getId().equals(sender.getId())) return receiver;
        if (receiver != null && me.getId().equals(receiver.getId())) return sender;
        return sender != null ? sender : receiver;
    }

    private String summarizeLastMessage(ChatMessage message) {
        if (message == null) return "";
        if (message.isDeletedForEveryone()) return "This message was deleted";
        String text = String.valueOf(message.getText() == null ? "" : message.getText()).trim();
        if (!text.isEmpty()) return text;
        String type = String.valueOf(message.getMediaType() == null ? "" : message.getMediaType()).toLowerCase();
        if (type.contains("image")) return "[Image]";
        if (type.contains("video")) return "[Video]";
        if (type.contains("audio")) return "Voice message";
        return message.getMediaUrl() != null && !message.getMediaUrl().isBlank() ? "[File]" : "";
    }

    private boolean isAfter(LocalDateTime a, LocalDateTime b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.isAfter(b);
    }

    private LocalDateTime parseTime(String raw) {
        try {
            if (raw == null || raw.equals("null")) return null;
            return LocalDateTime.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
