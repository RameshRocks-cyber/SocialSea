package com.socialsea.service;

import com.cloudinary.Cloudinary;
import com.socialsea.model.AnonymousPost;
import com.socialsea.repository.AnonymousPostRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AnonymousPostService {

    private final AnonymousPostRepository repository;
    private final Cloudinary cloudinary;

    public AnonymousPostService(AnonymousPostRepository repository, Cloudinary cloudinary) {
        this.repository = repository;
        this.cloudinary = cloudinary;
    }

    public AnonymousPost save(MultipartFile file, String description) {

        try {
            var uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                Map.of("resource_type", "video")
            );

            String videoUrl = uploadResult.get("secure_url").toString();

            AnonymousPost post = new AnonymousPost();
            post.setDescription(description);
            post.setContentUrl(videoUrl);
            post.setType("VIDEO");
            post.setApproved(false);
            post.setCreatedAt(LocalDateTime.now());

            AnonymousPost saved = repository.save(post);

            System.out.println("✅ SAVED WITH ID = " + saved.getId());
            System.out.println("🎥 VIDEO URL = " + videoUrl);

            return saved;

        } catch (Exception e) {
            throw new RuntimeException("Video upload failed", e);
        }
    }

    public List<AnonymousPost> getPendingPosts() {
        return repository.findByApprovedFalse();
    }

    public List<AnonymousPost> getApprovedPosts() {
        return repository.findByApprovedTrueOrderByCreatedAtDesc();
    }

    public AnonymousPost approvePost(Long id) {
        AnonymousPost post = repository.findById(id).orElseThrow(() -> new RuntimeException("Post not found"));
        post.setApproved(true);
        return repository.save(post);
    }

    public void deletePost(Long id) {
        repository.deleteById(id);
    }

    public void reject(Long id, String reason) {
        AnonymousPost post = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setApproved(false);
        post.setRejected(true);
        post.setRejectionReason(reason);

        repository.save(post);
        System.out.println("❌ Post rejected: " + id);
    }
}