package com.socialsea.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class OpenAiRealtimeService {
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String REALTIME_TOKEN_URL = "https://api.openai.com/v1/realtime/client_secrets";
    private static final String DEFAULT_MODEL = "gpt-realtime";
    private static final String DEFAULT_TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe";
    private static final String DEFAULT_MALE_VOICE = "alloy";
    private static final String DEFAULT_FEMALE_VOICE = "nova";
    private static final Pattern VOICE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(6))
            .readTimeout(Duration.ofSeconds(12))
            .writeTimeout(Duration.ofSeconds(12))
            .callTimeout(Duration.ofSeconds(16))
            .build();

    private final ObjectMapper mapper;

    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    @Value("${app.openai.voice.male:alloy}")
    private String maleVoice;

    @Value("${app.openai.voice.female:nova}")
    private String femaleVoice;

    private volatile String overrideKey;

    public OpenAiRealtimeService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode createRealtimeToken(String assistantName, String subject, String topic) {
        return createRealtimeToken(assistantName, subject, topic, null);
    }

    public JsonNode createRealtimeToken(String assistantName, String subject, String topic, String voice) {
        String resolvedKey = resolveApiKey();
        ensureConfigured(resolvedKey);
        String safeName = normalize(assistantName, "HRS");
        String safeSubject = normalize(subject, "Study");
        String safeTopic = normalize(topic, "");
        String resolvedVoice = resolveVoice(voice);

        ObjectNode root = mapper.createObjectNode();
        ObjectNode session = root.putObject("session");
        session.put("type", "realtime");
        session.put("model", DEFAULT_MODEL);
        session.put("instructions", buildInstructions(safeName, safeSubject, safeTopic));

        ArrayNode modalities = session.putArray("output_modalities");
        // Realtime currently supports a single output modality at a time.
        // The client can switch between "audio" and "text" via session.update.
        modalities.add("audio");

        ObjectNode audio = session.putObject("audio");
        audio.putObject("output").put("voice", resolvedVoice);

        String jsonBody = toJson(root);
        Request request = new Request.Builder()
                .url(REALTIME_TOKEN_URL)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + resolvedKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw buildRealtimeFailure(response.code(), body);
            }
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI realtime service unavailable",
                    e
            );
        }
    }

    private String buildInstructions(String assistantName, String subject, String topic) {
        String context = topic.isBlank() ? subject : topic;
        return """
                You are %s, a Study Mode voice assistant.
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

    public void setOverrideKey(String key) {
        String trimmed = normalizeKey(key);
        this.overrideKey = trimmed.isBlank() ? null : trimmed;
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
                    "OPENAI_API_KEY is not configured"
            );
        }
    }

    private ResponseStatusException buildRealtimeFailure(int statusCode, String body) {
        String normalized = normalizeProviderError(body);
        if (isCapacityError(normalized)) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Voice assistant is busy right now. Please try again in a moment."
            );
        }
        if (isRateLimitError(statusCode, normalized)) {
            return new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Voice assistant is getting a lot of traffic. Please try again shortly."
            );
        }
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Voice assistant service is temporarily unavailable."
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

    private String normalize(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isBlank() ? fallback : trimmed;
    }

    private String resolveVoice(String voice) {
        String requested = voice == null ? "" : voice.trim().toLowerCase();
        if (requested.isBlank() || "male".equals(requested) || "m".equals(requested)) {
            return normalizeVoiceId(maleVoice, DEFAULT_MALE_VOICE);
        }
        if ("female".equals(requested) || "f".equals(requested)) {
            return normalizeVoiceId(femaleVoice, DEFAULT_FEMALE_VOICE);
        }
        return normalizeVoiceId(requested, normalizeVoiceId(maleVoice, DEFAULT_MALE_VOICE));
    }

    private String normalizeVoiceId(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) return fallback;
        if (!VOICE_ID_PATTERN.matcher(trimmed).matches()) return fallback;
        return trimmed.toLowerCase();
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
