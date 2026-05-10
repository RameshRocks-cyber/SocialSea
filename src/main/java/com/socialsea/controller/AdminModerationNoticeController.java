package com.socialsea.controller;

import com.socialsea.model.Notification;
import com.socialsea.model.User;
import com.socialsea.repository.NotificationRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/admin/moderation/notices")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://43.205.229.211:5173"
})
public class AdminModerationNoticeController {
    private static final String TYPE_YELLOW = "MODERATION_YELLOW";
    private static final String TYPE_RED = "MODERATION_RED";
    private static final Pattern ISSUED_BY_PATTERN =
            Pattern.compile("^(.*?)(?:\\s*\\(Issued by\\s+([^\\)]+)\\)\\s*)?$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @GetMapping
    public List<Map<String, Object>> listModerationNotices(
            @RequestParam(name = "severity", required = false) String severityRaw,
            @RequestParam(name = "q", required = false) String queryRaw
    ) {
        List<String> types = resolveNoticeTypes(severityRaw);
        String query = normalizeQuery(queryRaw);
        Map<String, Optional<User>> userCache = new HashMap<>();

        return notificationRepository.findByTypeInOrderByCreatedAtDesc(types)
                .stream()
                .map((notice) -> toNoticeView(notice, userCache))
                .filter((row) -> matchesQuery(row, query))
                .toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeModerationNotice(@PathVariable Long id) {
        Optional<Notification> noticeOpt = notificationRepository.findById(id);
        if (noticeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Notice not found"));
        }

        Notification notice = noticeOpt.get();
        if (!isModerationType(notice.getType())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only moderation notices can be removed"));
        }

        notificationRepository.delete(notice);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "removedId", id
        ));
    }

    @PostMapping("/{id}/escalate-red")
    public ResponseEntity<?> escalateYellowNoticeToRed(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication
    ) {
        Optional<Notification> noticeOpt = notificationRepository.findById(id);
        if (noticeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Notice not found"));
        }

        Notification yellowNotice = noticeOpt.get();
        if (!TYPE_YELLOW.equalsIgnoreCase(safeString(yellowNotice.getType()))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only yellow notices can be escalated"));
        }

        String recipient = safeString(yellowNotice.getRecipient());
        if (recipient.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Notice recipient is missing"));
        }

        String issuedBy = authentication != null && authentication.getName() != null && !authentication.getName().isBlank()
                ? authentication.getName().trim()
                : "admin";
        String message = resolveEscalationMessage(body != null ? body.get("message") : null);
        String finalMessage = message + " (Issued by " + issuedBy + ")";

        notificationService.notifyUserInApp(recipient, "Red Notice", finalMessage, TYPE_RED);
        notificationRepository.delete(yellowNotice);

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(recipient);
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("removedId", yellowNotice.getId());
        response.put("severity", "red");
        response.put("recipient", recipient);
        response.put("userId", userOpt.map(User::getId).orElse(null));
        response.put("userEmail", userOpt.map(User::getEmail).orElse(recipient));
        response.put("userName", userOpt.map(this::displayName).orElse(recipient));
        response.put("message", message);
        response.put("issuedBy", issuedBy);
        response.put("createdAt", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/move-yellow")
    public ResponseEntity<?> moveRedNoticeToYellow(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication
    ) {
        Optional<Notification> noticeOpt = notificationRepository.findById(id);
        if (noticeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Notice not found"));
        }

        Notification redNotice = noticeOpt.get();
        if (!TYPE_RED.equalsIgnoreCase(safeString(redNotice.getType()))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only red notices can be moved to yellow"));
        }

        String recipient = safeString(redNotice.getRecipient());
        if (recipient.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Notice recipient is missing"));
        }

        String issuedBy = authentication != null && authentication.getName() != null && !authentication.getName().isBlank()
                ? authentication.getName().trim()
                : "admin";
        String message = resolveYellowMessage(body != null ? body.get("message") : null);
        String finalMessage = message + " (Issued by " + issuedBy + ")";

        notificationService.notifyUserInApp(recipient, "Yellow Notice", finalMessage, TYPE_YELLOW);
        notificationRepository.delete(redNotice);

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(recipient);
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("removedId", redNotice.getId());
        response.put("severity", "yellow");
        response.put("recipient", recipient);
        response.put("userId", userOpt.map(User::getId).orElse(null));
        response.put("userEmail", userOpt.map(User::getEmail).orElse(recipient));
        response.put("userName", userOpt.map(this::displayName).orElse(recipient));
        response.put("message", message);
        response.put("issuedBy", issuedBy);
        response.put("createdAt", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    private List<String> resolveNoticeTypes(String severityRaw) {
        String severity = normalizeSeverity(severityRaw);
        if ("yellow".equals(severity)) return List.of(TYPE_YELLOW);
        if ("red".equals(severity)) return List.of(TYPE_RED);
        return List.of(TYPE_YELLOW, TYPE_RED);
    }

    private String normalizeSeverity(String severityRaw) {
        String severity = safeString(severityRaw).toLowerCase(Locale.ROOT);
        if ("yellow".equals(severity)) return "yellow";
        if ("red".equals(severity)) return "red";
        return "";
    }

    private boolean isModerationType(String typeRaw) {
        String type = safeString(typeRaw);
        return TYPE_YELLOW.equalsIgnoreCase(type) || TYPE_RED.equalsIgnoreCase(type);
    }

    private Map<String, Object> toNoticeView(Notification notice, Map<String, Optional<User>> userCache) {
        String type = safeString(notice.getType());
        String severity = TYPE_YELLOW.equalsIgnoreCase(type) ? "yellow" : "red";
        String recipient = safeString(notice.getRecipient());
        String recipientKey = recipient.toLowerCase(Locale.ROOT);
        Optional<User> userOpt = userCache.computeIfAbsent(
                recipientKey,
                (key) -> recipient.isBlank() ? Optional.empty() : userRepository.findByEmailIgnoreCase(recipient)
        );

        NoticeMessageParts parts = splitNoticeMessage(safeString(notice.getMessage()));

        Map<String, Object> row = new HashMap<>();
        row.put("id", notice.getId());
        row.put("severity", severity);
        row.put("type", type);
        row.put("title", safeString(notice.getTitle()).isBlank() ? ("yellow".equals(severity) ? "Yellow Notice" : "Red Notice") : notice.getTitle());
        row.put("message", parts.message);
        row.put("issuedBy", parts.issuedBy);
        row.put("createdAt", notice.getCreatedAt());
        row.put("recipient", recipient);
        row.put("userId", userOpt.map(User::getId).orElse(null));
        row.put("userEmail", userOpt.map(User::getEmail).orElse(recipient));
        row.put("userName", userOpt.map(this::displayName).orElse(recipient));
        return row;
    }

    private boolean matchesQuery(Map<String, Object> row, String query) {
        if (query.isBlank()) return true;
        String haystack = (
                safeString(row.get("id")) + " " +
                        safeString(row.get("userName")) + " " +
                        safeString(row.get("userEmail")) + " " +
                        safeString(row.get("recipient")) + " " +
                        safeString(row.get("message")) + " " +
                        safeString(row.get("issuedBy"))
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(query);
    }

    private String normalizeQuery(String queryRaw) {
        return safeString(queryRaw).toLowerCase(Locale.ROOT);
    }

    private String safeString(Object raw) {
        if (raw == null) return "";
        return String.valueOf(raw).trim();
    }

    private String resolveEscalationMessage(Object rawMessage) {
        String message = safeString(rawMessage);
        if (message.isBlank()) {
            return "Critical violation recorded.";
        }
        if (message.length() > 600) {
            return message.substring(0, 600);
        }
        return message;
    }

    private String resolveYellowMessage(Object rawMessage) {
        String message = safeString(rawMessage);
        if (message.isBlank()) {
            return "Policy warning issued.";
        }
        if (message.length() > 600) {
            return message.substring(0, 600);
        }
        return message;
    }

    private String displayName(User user) {
        if (user == null) return "Unknown";
        if (user.getName() != null && !user.getName().isBlank()) return user.getName();
        if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
        return "User " + user.getId();
    }

    private NoticeMessageParts splitNoticeMessage(String rawMessage) {
        if (rawMessage.isBlank()) return new NoticeMessageParts("", "");
        Matcher matcher = ISSUED_BY_PATTERN.matcher(rawMessage);
        if (!matcher.matches()) {
            return new NoticeMessageParts(rawMessage, "");
        }
        String cleanMessage = safeString(matcher.group(1));
        String issuedBy = safeString(matcher.group(2));
        return new NoticeMessageParts(cleanMessage, issuedBy);
    }

    private static class NoticeMessageParts {
        private final String message;
        private final String issuedBy;

        private NoticeMessageParts(String message, String issuedBy) {
            this.message = message;
            this.issuedBy = issuedBy;
        }
    }
}
