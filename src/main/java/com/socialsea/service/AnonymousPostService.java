package com.socialsea.service;

import com.cloudinary.Cloudinary;
import com.socialsea.model.AnonymousPost;
import com.socialsea.repository.AnonymousPostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AnonymousPostService {

    private final AnonymousPostRepository repository;
    private final Cloudinary cloudinary;
    private final NotificationService notificationService;

    public AnonymousPostService(AnonymousPostRepository repository, Cloudinary cloudinary, NotificationService notificationService) {
        this.repository = repository;
        this.cloudinary = cloudinary;
        this.notificationService = notificationService;
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

            notificationService.notify(
                "🕵️ Anonymous Post",
                "New anonymous post pending approval",
                "ANON_POST"
            );

            System.out.println("✅ SAVED WITH ID = " + saved.getId());
            System.out.println("🎥 VIDEO URL = " + videoUrl);

            return saved;

        } catch (Exception e) {
            throw new RuntimeException("Video upload failed", e);
        }
    }

    public List<AnonymousPost> getPendingPosts() {
        return repository.findByApprovedFalseAndRejectedFalse();
    }

    public List<AnonymousPost> getApprovedPosts() {
        return repository.findByApprovedTrueOrderByCreatedAtDesc();
    }

    public void approvePost(Long id) {
        AnonymousPost post = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        post.setApproved(true);
        repository.save(post);
    }

    public void deletePost(Long id) {
        repository.deleteById(id);
    }

    public void rejectPost(Long id, String reason) {
        AnonymousPost post = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        post.setRejected(true);
        post.setRejectionReason(reason);
        repository.save(post);
    }

    @Transactional
    public void bulkApprove(List<Long> postIds) {
        List<AnonymousPost> posts = repository.findAllById(postIds);

        for (AnonymousPost post : posts) {
            post.setApproved(true);
            post.setRejected(false);
            post.setRejectionReason(null);
        }

        repository.saveAll(posts);
    }

    @Transactional
    public void bulkReject(List<Long> postIds, String reason) {
        List<AnonymousPost> posts = repository.findAllById(postIds);

        for (AnonymousPost post : posts) {
            post.setApproved(false);
            post.setRejected(true);
            post.setRejectionReason(reason);
        }

        repository.saveAll(posts);
    }
}