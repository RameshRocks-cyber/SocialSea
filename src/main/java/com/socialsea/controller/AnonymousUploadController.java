package com.socialsea.controller;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/anonymous")
@CrossOrigin(origins = "http://localhost:5173")
public class AnonymousUploadController {

    @Autowired
    private Cloudinary cloudinary;

    @PostMapping(
        value = "/upload",
        consumes = "multipart/form-data"
    )
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title
    ) throws Exception {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        // ✅ Upload to Cloudinary (VIDEO)
        Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                Map.of(
                        "resource_type", "video",
                        "folder", "anonymous"
                )
        );

        String videoUrl = uploadResult.get("secure_url").toString();

        // (Optional) Save to DB here
        // AnonymousPost post = new AnonymousPost();
        // post.setTitle(title);
        // post.setVideoUrl(videoUrl);
        // anonymousPostRepo.save(post);

        return ResponseEntity.ok(
                Map.of(
                        "message", "Uploaded successfully",
                        "videoUrl", videoUrl,
                        "title", title
                )
        );
    }
}
