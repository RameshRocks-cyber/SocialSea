package com.socialsea.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final Path uploadDir;

    public CloudinaryService(
        Cloudinary cloudinary,
        @Value("${app.upload.dir:uploads}") String uploadDir
    ) {
        this.cloudinary = cloudinary;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String upload(MultipartFile file) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult =
                    (Map<String, Object>) cloudinary.uploader().upload(
                            file.getBytes(),
                            ObjectUtils.asMap("resource_type", "auto")
                    );

            return uploadResult.get("secure_url").toString();
        } catch (Exception cloudinaryError) {
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
}
