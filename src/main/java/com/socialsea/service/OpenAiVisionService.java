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
import java.util.Base64;

@Service
public class OpenAiVisionService {
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(8))
            .readTimeout(Duration.ofSeconds(18))
            .writeTimeout(Duration.ofSeconds(18))
            .callTimeout(Duration.ofSeconds(22))
            .build();

    private final ObjectMapper mapper;

    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    @Value("${app.accessibility.sign.model:gpt-4o-mini}")
    private String model;

    public OpenAiVisionService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isBlank();
    }

    public SignTranslation translateSignToText(byte[] jpegBytes, String outputLang) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            return null;
        }
        if (!isConfigured()) {
            return null;
        }

        String safeLang = normalize(outputLang, "en");
        String base64 = Base64.getEncoder().encodeToString(jpegBytes);

        ObjectNode root = mapper.createObjectNode();
        root.put("model", normalize(model, "gpt-4o-mini"));
        root.put("temperature", 0.2);

        ArrayNode messages = root.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content",
                "You are a sign language interpreter for accessibility. " +
                        "Translate sign language from a single video frame image. " +
                        "If the sign cannot be reliably inferred from the image, respond with an empty text and low confidence.");

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");

        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text",
                "Translate the sign in this image into " + safeLang + ". " +
                        "Return ONLY a JSON object with keys: text (string), confidence (number 0-1), note (string). " +
                        "Do not add extra keys, markdown, or explanation.");

        ObjectNode image = content.addObject();
        image.put("type", "image_url");
        ObjectNode imageUrl = image.putObject("image_url");
        imageUrl.put("url", "data:image/jpeg;base64," + base64);

        String jsonBody = toJson(root);
        Request request = new Request.Builder()
                .url(CHAT_COMPLETIONS_URL)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + apiKey.trim())
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "OpenAI sign translation failed (status=" + response.code() + (body.isBlank() ? "" : ", body=" + body) + ")"
                );
            }
            JsonNode json = mapper.readTree(body);
            String contentText = extractAssistantContent(json);
            if (contentText == null || contentText.isBlank()) {
                return null;
            }
            JsonNode parsed = tryParseJsonObject(contentText);
            if (parsed == null || !parsed.isObject()) {
                // Fall back to plain text response if the model didn't return JSON
                return new SignTranslation(contentText.trim(), 0.35, "unstructured");
            }

            String translated = readText(parsed, "text");
            double confidence = readDouble(parsed, "confidence", 0.35);
            String note = readText(parsed, "note");
            if (note == null || note.isBlank()) note = "openai";
            return new SignTranslation(translated, clamp01(confidence), note);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI sign translation unavailable", e);
        }
    }

    public record SignTranslation(String text, double confidence, String note) {}

    private String extractAssistantContent(JsonNode response) {
        if (response == null) return null;
        JsonNode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) return null;
        JsonNode first = choices.get(0);
        if (first == null) return null;
        JsonNode message = first.get("message");
        if (message == null) return null;
        JsonNode content = message.get("content");
        if (content == null) return null;

        if (content.isTextual()) {
            return content.asText();
        }
        // Some clients/models may return structured content arrays.
        if (content.isArray()) {
            for (JsonNode part : content) {
                if (part == null) continue;
                JsonNode text = part.get("text");
                if (text != null && text.isTextual()) {
                    return text.asText();
                }
            }
        }
        return null;
    }

    private JsonNode tryParseJsonObject(String content) {
        if (content == null) return null;
        String trimmed = content.trim();
        try {
            return mapper.readTree(trimmed);
        } catch (Exception ignored) {
            // continue
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String slice = trimmed.substring(start, end + 1);
            try {
                return mapper.readTree(slice);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private String readText(JsonNode node, String field) {
        JsonNode v = node != null ? node.get(field) : null;
        if (v == null || v.isNull()) return null;
        return v.asText(null);
    }

    private double readDouble(JsonNode node, String field, double fallback) {
        JsonNode v = node != null ? node.get(field) : null;
        if (v == null || v.isNull()) return fallback;
        if (v.isNumber()) return v.asDouble();
        try {
            return Double.parseDouble(v.asText());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private String normalize(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isBlank() ? fallback : trimmed;
    }

    private String toJson(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build request");
        }
    }
}

