package com.socialsea.controller;

import com.socialsea.dto.FeedItemDto;
import com.socialsea.model.Post;
import com.socialsea.repository.PostRepository;
import com.socialsea.service.AnonymousPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/feed")
@CrossOrigin("https://socialsea.netlify.app")
@RequiredArgsConstructor
public class FeedController {

    private final AnonymousPostService anonymousPostService;
    private final PostRepository postRepo;

    @GetMapping
    public ResponseEntity<?> feed() {
        List<FeedItemDto> normalPosts = postRepo.findAll()
                .stream()
                .filter(Post::isApproved)
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .map(FeedItemDto::fromEntity)
                .toList();

        List<FeedItemDto> anonymousPosts = anonymousPostService.getApprovedFeed();

        List<FeedItemDto> items = Stream.concat(normalPosts.stream(), anonymousPosts.stream())
                .sorted(Comparator.comparing(FeedItemDto::getCreatedAt).reversed())
                .toList();

        return ResponseEntity.ok(items);
    }

    @GetMapping("/anonymous")
    public List<FeedItemDto> getAnonymousFeed() {
        return anonymousPostService.getApprovedFeed();
    }
}
