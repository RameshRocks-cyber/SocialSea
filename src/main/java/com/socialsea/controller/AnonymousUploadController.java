package com.socialsea.controller;

import com.cloudinary.Cloudinary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/anonymous")
@CrossOrigin
public class AnonymousUploadController {

    private final Cloudinary cloudinary;

    public AnonymousUploadController(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title
    ) throws Exception {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult =
                (Map<String, Object>) cloudinary.uploader().upload(
                        file.getBytes(),
                        Map.of(
                                "resource_type", "video",
                                "folder", "anonymous"
                        )
                );

        String videoUrl = uploadResult.get("secure_url").toString();

        return ResponseEntity.ok(
                Map.of(
                        "message", "Uploaded successfully",
                        "videoUrl", videoUrl,
                        "title", title
                )
        );
    }
}
