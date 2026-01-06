package com.socialsea.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

@RestController
@RequestMapping("/api/anonymous")
@CrossOrigin(origins = "http://localhost:5173")
public class AnonymousUploadController {

    private static final String UPLOAD_DIR = "uploads";

    @PostMapping(
        value = "/upload",
        consumes = "multipart/form-data"
    )

    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title
    ) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path path = Paths.get(UPLOAD_DIR, filename);
        Files.write(path, file.getBytes());

        return ResponseEntity.ok(
            Map.of(
                "message", "Uploaded successfully",
                "file", "/uploads/" + filename,
                "title", title
            )
        );
    }
}
