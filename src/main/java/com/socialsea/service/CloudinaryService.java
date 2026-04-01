package com.socialsea.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final Path uploadDir;
    private final Set<String> allowedTypes;
    private final Set<String> allowedExtensions;
    private final long maxBytes;
    private final boolean allowLocalFallback;

    public CloudinaryService(
        Cloudinary cloudinary,
        @Value("${app.upload.dir:uploads}") String uploadDir,
        @Value("${app.upload.allowed-types:image/png,image/jpeg,image/webp,image/gif,video/mp4,video/webm,audio/mpeg,audio/wav,audio/mp4,application/pdf}") String allowedTypes,
        @Value("${app.upload.allowed-extensions:png,jpg,jpeg,webp,gif,mp4,webm,mp3,wav,m4a,pdf}") String allowedExtensions,
        @Value("${app.upload.max-bytes:209715200}") long maxBytes,
        @Value("${app.upload.allow-local-fallback:false}") boolean allowLocalFallback
    ) {
        this.cloudinary = cloudinary;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.allowedTypes = parseCsv(allowedTypes);
        this.allowedExtensions = parseCsv(allowedExtensions);
        this.maxBytes = maxBytes;
        this.allowLocalFallback = allowLocalFallback;
    }

    public String upload(MultipartFile file) {
        validateFile(file);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult =
                    (Map<String, Object>) cloudinary.uploader().upload(
                            file.getBytes(),
                            ObjectUtils.asMap("resource_type", "auto")
                    );

            return uploadResult.get("secure_url").toString();
        } catch (Exception cloudinaryError) {
            if (!allowLocalFallback) {
                throw new RuntimeException("Upload failed. Cloudinary error: " + cloudinaryError.getMessage());
            }
            try {
                Files.createDirectories(uploadDir);

                String original = Objects.requireNonNullElse(file.getOriginalFilename(), "");
                String ext = "";
                int dot = original.lastIndexOf('.');
                if (dot >= 0) {
                    ext = original.substring(dot);
                }
                String name = UUID.randomUUID() + ext;
                Path target = uploadDir.resolve(name);
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

                return "/uploads/" + name;
            } catch (Exception localError) {
                throw new RuntimeException(
                    "Upload failed. Cloudinary error: " + cloudinaryError.getMessage() +
                    ". Local fallback error: " + localError.getMessage()
                );
            }
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File too large");
        }

        String contentType = normalize(file.getContentType());
        if (!allowedTypes.isEmpty() && (contentType == null || !allowedTypes.contains(contentType))) {
            throw new IllegalArgumentException("Unsupported file type");
        }

        String ext = extractExtension(file.getOriginalFilename());
        if (!allowedExtensions.isEmpty() && (ext == null || !allowedExtensions.contains(ext))) {
            throw new IllegalArgumentException("Unsupported file extension");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return null;
        }
        String trimmed = filename.trim();
        int dot = trimmed.lastIndexOf('.');
        if (dot < 0 || dot == trimmed.length() - 1) {
            return null;
        }
        return trimmed.substring(dot + 1).toLowerCase();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Set<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toCollection(HashSet::new));
    }
}
