package com.socialsea.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class RuntimeConfigValidator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigValidator.class);

    private final Environment environment;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${app.upload.provider:cloudinary}")
    private String uploadProvider;

    @Value("${cloudinary.cloud_name:}")
    private String cloudinaryCloudName;

    @Value("${cloudinary.api_key:}")
    private String cloudinaryApiKey;

    @Value("${cloudinary.api_secret:}")
    private String cloudinaryApiSecret;

    @Value("${app.s3.bucket:}")
    private String s3Bucket;

    @Value("${app.upload.allow-local-fallback:false}")
    private boolean allowLocalFallback;

    @Value("${app.upload.serve-local:false}")
    private boolean serveLocalUploads;

    @Value("${app.runtime.ec2:}")
    private String ec2Override;

    @Value("${app.runtime.enforce-prod-on-ec2:true}")
    private boolean enforceProdOnEc2;

    public RuntimeConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.stream(environment.getActiveProfiles())
            .map(profile -> profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT))
            .anyMatch("prod"::equals);
        boolean ec2Runtime = detectEc2Runtime();

        if (ec2Runtime && enforceProdOnEc2 && !prod) {
            throw new IllegalStateException(
                "EC2 runtime detected but SPRING_PROFILES_ACTIVE is not 'prod'. " +
                "Set SPRING_PROFILES_ACTIVE=prod."
            );
        }

        if (!prod) {
            return;
        }

        String provider = uploadProvider == null ? "" : uploadProvider.trim().toLowerCase(Locale.ROOT);
        if (provider.isBlank()) {
            provider = "cloudinary";
        }

        List<String> missing = new ArrayList<>();
        require("SPRING_DATASOURCE_URL", datasourceUrl, missing);
        switch (provider) {
            case "cloudinary" -> {
                require("CLOUDINARY_CLOUD_NAME", cloudinaryCloudName, missing);
                require("CLOUDINARY_API_KEY", cloudinaryApiKey, missing);
                require("CLOUDINARY_API_SECRET", cloudinaryApiSecret, missing);
            }
            case "s3" -> require("APP_S3_BUCKET", s3Bucket, missing);
            default -> throw new IllegalStateException(
                "Unknown app.upload.provider='" + provider + "'. Supported values: cloudinary, s3."
            );
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required production configuration: " + String.join(", ", missing)
            );
        }

        if (allowLocalFallback || serveLocalUploads) {
            log.warn(
                "Production upload safety warning: local upload fallback is enabled " +
                "(app.upload.allow-local-fallback={}, app.upload.serve-local={}).",
                allowLocalFallback,
                serveLocalUploads
            );
        }

        log.info(
            "Production runtime config validated. datasourceHost={}, uploadProvider={}, cloudinaryCloudName={}, s3Bucket={}, ec2Runtime={}",
            extractHost(datasourceUrl),
            provider,
            cloudinaryCloudName,
            s3Bucket,
            ec2Runtime
        );
    }

    private void require(String key, String value, List<String> missing) {
        if (isMissing(value)) {
            missing.add(key);
        }
    }

    private boolean isMissing(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return true;
        }
        if (normalized.startsWith("<") && normalized.endsWith(">")) {
            return true;
        }
        return normalized.matches(".*<[^>]+>.*");
    }

    private String extractHost(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "unknown";
        }
        String trimmed = jdbcUrl.trim();
        if (trimmed.startsWith("jdbc:")) {
            trimmed = trimmed.substring(5);
        }
        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "unknown";
            }
            return host;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private boolean detectEc2Runtime() {
        Boolean override = parseBooleanFlag(ec2Override);
        if (override != null) {
            return override;
        }

        String ec2InstanceId = System.getenv("EC2_INSTANCE_ID");
        if (hasText(ec2InstanceId)) {
            return true;
        }

        String awsExecutionEnv = System.getenv("AWS_EXECUTION_ENV");
        if (hasText(awsExecutionEnv) && awsExecutionEnv.toLowerCase(Locale.ROOT).contains("ec2")) {
            return true;
        }

        return fileStartsWithIgnoreCase("/sys/hypervisor/uuid", "ec2")
            || fileStartsWithIgnoreCase("/sys/devices/virtual/dmi/id/product_uuid", "ec2")
            || fileContainsIgnoreCase("/sys/devices/virtual/dmi/id/sys_vendor", "amazon")
            || fileContainsIgnoreCase("/sys/devices/virtual/dmi/id/board_vendor", "amazon")
            || fileContainsIgnoreCase("/sys/devices/virtual/dmi/id/bios_vendor", "amazon");
    }

    private Boolean parseBooleanFlag(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value) || "1".equals(value) || "yes".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value) || "0".equals(value) || "no".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean fileStartsWithIgnoreCase(String filePath, String prefix) {
        String line = safeReadFirstLine(filePath);
        return line != null && line.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private boolean fileContainsIgnoreCase(String filePath, String needle) {
        String line = safeReadFirstLine(filePath);
        return line != null && line.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String safeReadFirstLine(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty()) {
                return null;
            }
            String first = lines.get(0);
            return first == null ? null : first.trim();
        } catch (IOException ignored) {
            return null;
        }
    }
}
