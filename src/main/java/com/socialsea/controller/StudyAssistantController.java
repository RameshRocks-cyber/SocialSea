package com.socialsea.controller;

import com.socialsea.service.GeminiService;
import com.socialsea.service.OpenAiRealtimeService;
import com.socialsea.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping({"/api/study/assistant", "/api/public/study-assistant"})
public class StudyAssistantController {

    private final OpenAiRealtimeService realtimeService;
    private final GeminiService geminiService;
    private final UploadService uploadService;
    private final Environment environment;

    public StudyAssistantController(
        OpenAiRealtimeService realtimeService,
        GeminiService geminiService,
        UploadService uploadService,
        Environment environment
    ) {
        this.realtimeService = realtimeService;
        this.geminiService = geminiService;
        this.uploadService = uploadService;
        this.environment = environment;
    }

    @PostMapping("/realtime-token")
    public ResponseEntity<?> createRealtimeToken(@RequestBody(required = false) RealtimeTokenRequest request) {
        String assistantName = request != null ? request.getAssistantName() : null;
        String subject = request != null ? request.getSubject() : null;
        String topic = request != null ? request.getTopic() : null;
        String voice = request != null ? request.getVoice() : null;

        try {
            return ResponseEntity.ok(realtimeService.createRealtimeToken(assistantName, subject, topic, voice));
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            String message = ex.getReason() != null ? ex.getReason() : "Realtime token error";
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
        }
    }

    @PostMapping("/set-key")
    public ResponseEntity<?> setApiKey(
        @RequestBody(required = false) ApiKeyRequest request,
        HttpServletRequest httpRequest
    ) {
        if (!isDevProfile() || !isLoopback(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }
        String key = request != null ? request.getApiKey() : null;
        realtimeService.setOverrideKey(key);
        boolean enabled = key != null && !key.trim().isBlank();
        return ResponseEntity.ok(Map.of("ok", true, "enabled", enabled));
    }

    @GetMapping("/gemini/status")
    public Map<String, Object> geminiStatus() {
        return Map.of("configured", geminiService != null && geminiService.isConfigured());
    }

    @PostMapping("/gemini")
    public ResponseEntity<?> geminiChat(@RequestBody(required = false) GeminiChatRequest request) {
        String assistantName = request != null ? request.getAssistantName() : null;
        String subject = request != null ? request.getSubject() : null;
        String topic = request != null ? request.getTopic() : null;
        List<GeminiChatRequest.ChatMessage> incoming = request != null ? request.getMessages() : null;

        List<GeminiService.ChatMessage> messages = new ArrayList<>();
        if (incoming != null) {
            for (GeminiChatRequest.ChatMessage msg : incoming) {
                if (msg == null) continue;
                messages.add(new GeminiService.ChatMessage(msg.getRole(), msg.getText()));
            }
        }

        try {
            String text = geminiService.generateStudyAssistantReply(assistantName, subject, topic, messages);
            return ResponseEntity.ok(Map.of("text", text));
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            String message = ex.getReason() != null ? ex.getReason() : "Gemini error";
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
        }
    }

    @PostMapping("/set-gemini-key")
    public ResponseEntity<?> setGeminiKey(
        @RequestBody(required = false) ApiKeyRequest request,
        HttpServletRequest httpRequest
    ) {
        if (!isDevProfile() || !isLoopback(httpRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }
        String key = request != null ? request.getApiKey() : null;
        geminiService.setOverrideKey(key);
        boolean enabled = key != null && !key.trim().isBlank();
        return ResponseEntity.ok(Map.of("ok", true, "enabled", enabled));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadStudyFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is required"));
        }
        String url = uploadService.upload(file);
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", url);
        payload.put("name", file.getOriginalFilename());
        payload.put("size", file.getSize());
        payload.put("type", file.getContentType());
        return ResponseEntity.status(HttpStatus.OK).body(payload);
    }

    public static class RealtimeTokenRequest {
        private String assistantName;
        private String subject;
        private String topic;
        private String voice;

        public String getAssistantName() {
            return assistantName;
        }

        public void setAssistantName(String assistantName) {
            this.assistantName = assistantName;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getVoice() {
            return voice;
        }

        public void setVoice(String voice) {
            this.voice = voice;
        }
    }

    public static class ApiKeyRequest {
        private String apiKey;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class GeminiChatRequest {
        private String assistantName;
        private String subject;
        private String topic;
        private List<ChatMessage> messages;

        public String getAssistantName() {
            return assistantName;
        }

        public void setAssistantName(String assistantName) {
            this.assistantName = assistantName;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public List<ChatMessage> getMessages() {
            return messages;
        }

        public void setMessages(List<ChatMessage> messages) {
            this.messages = messages;
        }

        public static class ChatMessage {
            private String role;
            private String text;

            public String getRole() {
                return role;
            }

            public void setRole(String role) {
                this.role = role;
            }

            public String getText() {
                return text;
            }

            public void setText(String text) {
                this.text = text;
            }
        }
    }

    private boolean isDevProfile() {
        return environment != null && environment.acceptsProfiles(Profiles.of("dev"));
    }

    private boolean isLoopback(HttpServletRequest request) {
        if (request == null) return false;
        String addr = request.getRemoteAddr();
        if (addr == null) return false;
        return "127.0.0.1".equals(addr)
            || "::1".equals(addr)
            || "0:0:0:0:0:0:0:1".equals(addr);
    }
}
