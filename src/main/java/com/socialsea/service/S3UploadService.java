package com.socialsea.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.upload.provider", havingValue = "s3")
public class S3UploadService implements UploadService {

    private final S3Client s3;
    private final String bucket;
    private final String prefix;
    private final String publicBaseUrl;
    private final Path uploadDir;
    private final Set<String> allowedTypes;
    private final Set<String> allowedExtensions;
    private final long maxBytes;
    private final boolean allowLocalFallback;

    public S3UploadService(
        @Value("${app.s3.bucket:}") String bucket,
        @Value("${app.s3.prefix:socialsea}") String prefix,
        @Value("${app.s3.public-base-url:}") String publicBaseUrl,
        @Value("${app.s3.region:}") String region,
        @Value("${app.upload.dir:uploads}") String uploadDir,
        @Value("${app.upload.allowed-types:image/png,image/jpeg,image/webp,image/gif,video/mp4,video/webm,video/x-matroska,video/matroska,audio/mpeg,audio/wav,audio/mp4,application/pdf}") String allowedTypes,
        @Value("${app.upload.allowed-extensions:png,jpg,jpeg,webp,gif,mp4,webm,mkv,mp3,wav,m4a,pdf}") String allowedExtensions,
        @Value("${app.upload.max-bytes:1073741824}") long maxBytes,
        @Value("${app.upload.allow-local-fallback:false}") boolean allowLocalFallback
    ) {
        this.bucket = bucket == null ? "" : bucket.trim();
        this.prefix = normalizePrefix(prefix);
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.allowedTypes = parseCsv(allowedTypes);
        this.allowedExtensions = parseCsv(allowedExtensions);
        this.maxBytes = maxBytes;
        this.allowLocalFallback = allowLocalFallback;

        if (region != null && !region.trim().isEmpty()) {
            this.s3 = S3Client.builder().region(Region.of(region.trim())).build();
        } else {
            this.s3 = S3Client.builder().build();
        }
    }

    @Override
    public String upload(MultipartFile file) {
        validateFile(file);
        if (bucket.isBlank()) {
            throw new IllegalStateException("APP_S3_BUCKET must be configured when app.upload.provider=s3");
        }

        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }

        String key = (prefix.isBlank() ? "" : prefix + "/") + UUID.randomUUID() + ext;
        try {
            String contentType = file.getContentType();
            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = "application/octet-stream";
            }

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

            long size = file.getSize();
            if (size > 0) {
                s3.putObject(request, RequestBody.fromInputStream(file.getInputStream(), size));
            } else {
                s3.putObject(request, RequestBody.fromBytes(file.getBytes()));
            }

            return buildPublicUrl(key);
        } catch (Exception s3Error) {
            if (!allowLocalFallback) {
                throw new RuntimeException("Upload failed. S3 error: " + s3Error.getMessage());
            }
            try {
                Files.createDirectories(uploadDir);

                String name = UUID.randomUUID() + ext;
                Path target = uploadDir.resolve(name);
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

                return "/uploads/" + name;
            } catch (Exception localError) {
                throw new RuntimeException(
                    "Upload failed. S3 error: " + s3Error.getMessage() +
                        ". Local fallback error: " + localError.getMessage()
                );
            }
        }
    }

    private String buildPublicUrl(String key) {
        if (!publicBaseUrl.isBlank()) {
            String base = publicBaseUrl;
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base + "/" + key;
        }

        String region = null;
        try {
            Region resolved = s3.serviceClientConfiguration().region();
            region = resolved != null ? resolved.id() : null;
        } catch (Exception ignored) {
            // best-effort
        }
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }

        // Virtual-hosted style is preferred, but HTTPS wildcard cert doesn't match buckets with dots.
        if (bucket.contains(".")) {
            return "https://s3." + region + ".amazonaws.com/" + bucket + "/" + key;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    private String normalizePrefix(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
