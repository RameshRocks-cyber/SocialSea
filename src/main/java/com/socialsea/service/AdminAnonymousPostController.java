package com.socialsea.service;

import com.socialsea.model.AnonymousPost;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/anonymous")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "https://your-netlify-site.netlify.app"})
public class AdminAnonymousPostController {

    private final AnonymousPostService anonymousPostService;

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('POST_APPROVE')")
    public List<AnonymousPost> getPendingPosts() {
        System.out.println("📥 Admin fetching pending posts...");
        return anonymousPostService.getPendingPosts();
    }

    @GetMapping("/approved")
    public List<AnonymousPost> getApprovedPosts() {
        return anonymousPostService.getApprovedPosts();
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<Void> approvePost(@PathVariable Long id) {
        anonymousPostService.approvePost(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Void> rejectPost(@PathVariable Long id, @RequestBody Map<String, String> body) {
        anonymousPostService.rejectPost(id, body.get("reason"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        anonymousPostService.deletePost(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk-approve")
    public ResponseEntity<Void> bulkApprove(@RequestBody List<Long> ids) {
        anonymousPostService.bulkApprove(ids);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk-reject")
    public ResponseEntity<Void> bulkReject(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        String reason = (String) body.get("reason");
        anonymousPostService.bulkReject(ids, reason);
        return ResponseEntity.ok().build();
    }
}
