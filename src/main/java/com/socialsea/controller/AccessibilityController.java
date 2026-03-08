package com.socialsea.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accessibility")
public class AccessibilityController {

    @PostMapping("/sign-to-text")
    public ResponseEntity<?> signToText(
            @RequestParam("frame") MultipartFile frame,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "contactId", required = false) String contactId,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        if (frame == null || frame.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing sign frame image"));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("lang", lang == null ? "" : lang);
        body.put("contactId", contactId == null ? "" : contactId);
        body.put("mode", "assist");

        try {
            BufferedImage image = ImageIO.read(frame.getInputStream());
            if (image == null) {
                body.put("text", "Could not read sign image. Keep hand visible and try again.");
                body.put("confidence", 0.0);
                body.put("note", "invalid_image");
                return ResponseEntity.ok(body);
            }

            double brightness = estimateBrightness(image);
            double contrast = estimateContrast(image, brightness);

            String text;
            double confidence;
            String note;

            if (brightness < 45) {
                text = "Please increase lighting and sign again.";
                confidence = 0.22;
                note = "low_light";
            } else if (contrast < 18) {
                text = "Move hand closer and keep background plain for better sign detection.";
                confidence = 0.26;
                note = "low_contrast";
            } else {
                text = "Sign captured. Please review and send.";
                confidence = 0.41;
                note = "captured";
            }

            body.put("text", text);
            body.put("confidence", confidence);
            body.put("note", note);
            return ResponseEntity.ok(body);
        } catch (IOException e) {
            body.put("text", "Capture failed. Try again.");
            body.put("confidence", 0.0);
            body.put("note", "io_error");
            return ResponseEntity.ok(body);
        }
    }

    private double estimateBrightness(BufferedImage image) {
        long total = 0;
        long count = 0;
        int stepX = Math.max(1, image.getWidth() / 120);
        int stepY = Math.max(1, image.getHeight() / 120);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                total += (r + g + b) / 3;
                count++;
            }
        }
        return count == 0 ? 0 : (double) total / count;
    }

    private double estimateContrast(BufferedImage image, double mean) {
        double variance = 0;
        long count = 0;
        int stepX = Math.max(1, image.getWidth() / 120);
        int stepY = Math.max(1, image.getHeight() / 120);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                double l = (r + g + b) / 3.0;
                double d = l - mean;
                variance += d * d;
                count++;
            }
        }
        if (count == 0) return 0;
        return Math.sqrt(variance / count);
    }
}
