package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.AnonymousPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeedControllerPaginationTest {

    @InjectMocks
    private FeedController controller;

    @Mock
    private AnonymousPostService anonymousPostService;

    @Mock
    private PostRepository postRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private FollowRepository followRepo;

    @Mock
    private StoryRepository storyRepo;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "includeUnapproved", false);
        ReflectionTestUtils.setField(controller, "maxFeedItems", 20);
        ReflectionTestUtils.setField(controller, "maxVideoItems", 20);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void feedHonorsRequestedPageSizeAndSignalsNextPage() throws Exception {
        when(storyRepo.findActiveMediaUrls(any())).thenReturn(List.of());
        when(postRepo.findApprovedFeedCandidates(any())).thenReturn(buildFeedPosts(21));

        mockMvc.perform(get("/api/feed").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.content", hasSize(20)))
            .andExpect(jsonPath("$.content[0].id").value(1));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepo).findApprovedFeedCandidates(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(0, pageable.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(21, pageable.getPageSize());
    }

    private List<Post> buildFeedPosts(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
            .mapToObj(this::createPost)
            .toList();
    }

    private Post createPost(int index) {
        User user = new User();
        user.setId(1_000L + index);
        user.setName("Creator " + index);
        user.setPrivateAccount(false);

        Post post = new Post();
        post.setId((long) index);
        post.setUser(user);
        post.setApproved(true);
        post.setReel(false);
        post.setMediaUrl("https://cdn.socialsea.test/post-" + index + ".jpg");
        post.setTitle("Title " + index);
        post.setDescription("Description " + index);
        return post;
    }
}
