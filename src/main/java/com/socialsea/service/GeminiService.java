package com.socialsea.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class GeminiService {
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";
    private static final int MAX_MESSAGES = 20;
    private static final int MAX_TEXT_CHARS = 12000;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(8))
            .readTimeout(Duration.ofSeconds(22))
            .writeTimeout(Duration.ofSeconds(22))
            .callTimeout(Duration.ofSeconds(26))
            .build();

    private final ObjectMapper mapper;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    @Value("${app.gemini.model:" + DEFAULT_MODEL + "}")
    private String model;

    private volatile String overrideKey;

    public GeminiService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return !resolveApiKey().isBlank();
    }

    public void setOverrideKey(String key) {
        String trimmed = normalizeKey(key);
        this.overrideKey = trimmed.isBlank() ? null : trimmed;
    }

    public String generateStudyAssistantReply(
            String assistantName,
            String subject,
            String topic,
            List<ChatMessage> messages
    ) {
        String resolvedKey = resolveApiKey();
        ensureConfigured(resolvedKey);
        String safeName = normalize(assistantName, "HRS");
        String safeSubject = normalize(subject, "Study");
        String safeTopic = normalize(topic, "");

        List<ChatMessage> safeMessages = normalizeMessages(messages);
        if (safeMessages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messages is required");
        }

        ObjectNode root = mapper.createObjectNode();
        root.set("systemInstruction", buildSystemInstruction(buildInstructions(safeName, safeSubject, safeTopic)));

        ArrayNode contents = root.putArray("contents");
        for (ChatMessage msg : safeMessages) {
            ObjectNode content = contents.addObject();
            content.put("role", mapRole(msg.role()));
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", msg.text());
        }

        ObjectNode config = root.putObject("generationConfig");
        config.put("temperature", 0.35);
        config.put("maxOutputTokens", 900);

        HttpUrl url = HttpUrl.parse(BASE_URL + normalize(model, DEFAULT_MODEL) + ":generateContent");
        if (url == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid Gemini endpoint URL");
        }
        url = url.newBuilder().addQueryParameter("key", resolvedKey).build();

        String jsonBody = toJson(root);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw buildGeminiFailure(response.code(), body);
            }
            JsonNode json = mapper.readTree(body);
            String text = extractText(json);
            if (text == null || text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini returned an empty response");
            }
            return text.trim();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini service unavailable", e);
        }
    }

    public record ChatMessage(String role, String text) {}

    private ObjectNode buildSystemInstruction(String instructionText) {
        ObjectNode content = mapper.createObjectNode();
        content.put("role", "system");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", instructionText);
        return content;
    }

    private List<ChatMessage> normalizeMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return List.of();
        List<ChatMessage> out = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg == null) continue;
            String text = normalizeText(msg.text());
            if (text.isBlank()) continue;
            out.add(new ChatMessage(normalizeRole(msg.role()), text));
            if (out.size() >= MAX_MESSAGES) break;
        }
        return out;
    }

    private String normalizeRole(String role) {
        String r = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if (r.isBlank()) return "user";
        if ("assistant".equals(r)) return "model";
        if ("model".equals(r)) return "model";
        if ("system".equals(r)) return "system";
        return "user";
    }

    private String mapRole(String normalizedRole) {
        if ("model".equals(normalizedRole)) return "model";
        if ("system".equals(normalizedRole)) return "system";
        return "user";
    }

    private String normalizeText(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) return "";
        if (trimmed.length() <= MAX_TEXT_CHARS) return trimmed;
        return trimmed.substring(0, MAX_TEXT_CHARS);
    }

    private String extractText(JsonNode response) {
        if (response == null) return null;
        JsonNode candidates = response.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) return null;
        JsonNode first = candidates.get(0);
        if (first == null) return null;
        JsonNode content = first.get("content");
        if (content == null) return null;
        JsonNode parts = content.get("parts");
        if (parts == null || !parts.isArray() || parts.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part == null) continue;
            JsonNode text = part.get("text");
            if (text != null && text.isTextual()) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(text.asText());
            }
        }
        String out = sb.toString().trim();
        return out.isBlank() ? null : out;
    }

    private String buildInstructions(String assistantName, String subject, String topic) {
        String context = topic.isBlank() ? subject : topic;
        return """
                You are %s, a Study Mode assistant.
                Respond like Siri: concise, friendly, and helpful.
                Focus on the current study context: %s.
                When asked about a topic, produce clear study notes in bullet points.
                If the user wants a PPT, return a slide-by-slide outline.
                If asked for a novel or music album, return a structured creative outline.
                If the user provides long notes, summarize them into short bullet points.
                If asked to fix dates, return the corrected notes only.
                Keep answers short unless the user asks for detail.
                """.formatted(assistantName, context);
    }

    private ResponseStatusException buildGeminiFailure(int statusCode, String body) {
        String normalized = normalizeProviderError(body);
        if (isCapacityError(normalized)) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Study assistant is busy right now. Please try again in a moment."
            );
        }
        if (isRateLimitError(statusCode, normalized)) {
            return new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Study assistant is getting a lot of traffic. Please try again shortly."
            );
        }
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Study assistant service is temporarily unavailable."
        );
    }

    private String normalizeProviderError(String body) {
        String raw = body == null ? "" : body.trim();
        if (raw.isBlank()) return "";
        try {
            JsonNode parsed = mapper.readTree(raw);
            JsonNode error = parsed.path("error");
            if (!error.isMissingNode()) {
                String message = error.path("message").asText("");
                if (!message.isBlank()) return message.trim().toLowerCase(Locale.ROOT);
            }
            String message = parsed.path("message").asText("");
            if (!message.isBlank()) return message.trim().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            // Fall through to raw-text matching.
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private boolean isCapacityError(String normalized) {
        if (normalized == null || normalized.isBlank()) return false;
        return normalized.contains("at capacity")
                || normalized.contains("try a different model")
                || normalized.contains("model is overloaded")
                || normalized.contains("server is overloaded")
                || normalized.contains("resource exhausted");
    }

    private boolean isRateLimitError(int statusCode, String normalized) {
        if (statusCode == 429) return true;
        if (normalized == null || normalized.isBlank()) return false;
        return normalized.contains("rate limit")
                || normalized.contains("too many requests")
                || normalized.contains("quota");
    }

    private String resolveApiKey() {
        String trimmedOverride = normalizeKey(overrideKey);
        if (!trimmedOverride.isBlank()) {
            return trimmedOverride;
        }
        return normalizeKey(apiKey);
    }

    private void ensureConfigured(String resolvedKey) {
        if (resolvedKey == null || resolvedKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GEMINI_API_KEY is not configured"
            );
        }
    }

    private String normalize(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isBlank() ? fallback : trimmed;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim();
    }

    private String toJson(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build request");
        }
    }
}
