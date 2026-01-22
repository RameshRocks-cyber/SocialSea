package com.socialsea.controller;

import com.socialsea.service.AnonymousPostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/anonymous")
@CrossOrigin("https://socialsea.netlify.app")
public class AnonymousPostController {

    private final AnonymousPostService service;

    public AnonymousPostController(AnonymousPostService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadAnonymous(
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description
    ) {
        System.out.println("🔥 UPLOAD CONTROLLER HIT 🔥");
        System.out.println("File name = " + file.getOriginalFilename());
        System.out.println("Description = " + description);

        service.save(file, description);

        return ResponseEntity.ok("Uploaded. Waiting for admin approval.");
    }
}