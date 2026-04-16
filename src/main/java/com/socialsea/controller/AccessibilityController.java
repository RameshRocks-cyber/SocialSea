package com.socialsea.controller;

import com.socialsea.service.OpenAiVisionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accessibility")
public class AccessibilityController {

    private final OpenAiVisionService openAiVisionService;

    public AccessibilityController(OpenAiVisionService openAiVisionService) {
        this.openAiVisionService = openAiVisionService;
    }

    @PostMapping("/sign-to-text")
    public ResponseEntity<?> signToText(
            @RequestParam(value = "frame", required = false) MultipartFile frame,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "contactId", required = false) String contactId,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        MultipartFile resolved = firstNonEmpty(frame, image, file);

        if (resolved == null || resolved.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing sign frame image"));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("lang", lang == null ? "" : lang);
        body.put("contactId", contactId == null ? "" : contactId);
        body.put("mode", "assist");

        try {
            byte[] uploadedBytes = resolved.getBytes();
            BufferedImage imageDecoded = ImageIO.read(new ByteArrayInputStream(uploadedBytes));
            if (imageDecoded == null) {
                body.put("text", "Could not read sign image. Keep hand visible and try again.");
                body.put("confidence", 0.0);
                body.put("note", "invalid_image");
                return ResponseEntity.ok(body);
            }

            double brightness = estimateBrightness(imageDecoded);
            double contrast = estimateContrast(imageDecoded, brightness);

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
                OpenAiVisionService.SignTranslation translation = null;
                boolean translationFailed = false;
                try {
                    byte[] normalizedJpeg = normalizeForVision(imageDecoded);
                    translation = openAiVisionService.translateSignToText(normalizedJpeg, body.get("lang").toString());
                } catch (Exception ignored) {
                    // Fall back to simple capture response if vision translation fails.
                    translationFailed = true;
                }

                if (translation != null && translation.text() != null && !translation.text().isBlank()) {
                    text = translation.text();
                    confidence = translation.confidence();
                    note = translation.note();
                } else if (!openAiVisionService.isConfigured()) {
                    text = "Sign translation is not configured. Please contact support.";
                    confidence = 0.18;
                    note = "not_configured";
                } else if (translationFailed) {
                    text = "Sign captured, but translation failed. Please try again.";
                    confidence = 0.25;
                    note = "translate_error";
                } else {
                    text = "Sign captured. Please review and send.";
                    confidence = 0.41;
                    note = "captured";
                }
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

    private MultipartFile firstNonEmpty(MultipartFile... candidates) {
        if (candidates == null) return null;
        for (MultipartFile f : candidates) {
            if (f != null && !f.isEmpty()) return f;
        }
        return null;
    }

    private byte[] normalizeForVision(BufferedImage src) throws IOException {
        if (src == null) return new byte[0];
        BufferedImage scaled = downscale(src, 640);
        return encodeJpeg(scaled, 0.75f);
    }

    private BufferedImage downscale(BufferedImage src, int maxDim) {
        int width = src.getWidth();
        int height = src.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maxDim) {
            return src;
        }
        double scale = (double) maxDim / (double) largest;
        int targetW = Math.max(1, (int) Math.round(width * scale));
        int targetH = Math.max(1, (int) Math.round(height * scale));
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private byte[] encodeJpeg(BufferedImage src, float quality) throws IOException {
        if (src == null) return new byte[0];
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").hasNext()
                ? ImageIO.getImageWritersByFormatName("jpg").next()
                : null;
        if (writer == null) {
            // Fallback: PNG if no JPEG writer is available (unlikely on standard JVMs).
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(rgb, "png", baos);
            return baos.toByteArray();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, quality)));
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
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
