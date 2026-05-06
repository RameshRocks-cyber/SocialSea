package com.socialsea.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * MultipartFile backed by a filesystem path, used for processed media artifacts.
 */
public class PathMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final Path path;

    public PathMultipartFile(String name, String originalFilename, String contentType, Path path) {
        this.name = (name == null || name.isBlank()) ? "file" : name;
        this.originalFilename = (originalFilename == null || originalFilename.isBlank())
                ? path.getFileName().toString()
                : originalFilename;
        this.contentType = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        try {
            return Files.size(path) <= 0;
        } catch (IOException ignored) {
            return true;
        }
    }

    @Override
    public long getSize() {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return 0L;
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Objects.requireNonNull(dest, "dest");
        transferTo(dest.toPath());
    }

    @Override
    public void transferTo(Path dest) throws IOException, IllegalStateException {
        Objects.requireNonNull(dest, "dest");
        Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
    }
}
