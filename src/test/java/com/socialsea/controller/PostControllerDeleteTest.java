package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.CommentRepository;
import com.socialsea.repository.LikeRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.SavedPostRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.UploadService;
import com.socialsea.service.VideoEditingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerDeleteTest {

    private static final Long POST_ID = 7L;
    private static final Long OWNER_ID = 11L;
    private static final String OWNER_EMAIL = "owner@example.com";

    @InjectMocks
    private PostController controller;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private SavedPostRepository savedPostRepository;

    @Mock
    private UploadService uploadService;

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private VideoEditingService videoEditingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void deletePostRemovesPostAndRelatedRecords() throws Exception {
        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setEmail(OWNER_EMAIL);
        owner.setRole(Role.USER);

        Post post = new Post();
        post.setId(POST_ID);
        post.setUser(owner);

        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        mockMvc.perform(delete("/api/posts/{id}", POST_ID)
                        .principal(new UsernamePasswordAuthenticationToken(
                                OWNER_EMAIL,
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.deletedId").value(POST_ID));

        verify(commentRepository).deleteByPost(post);
        verify(likeRepository).deleteByPost(post);
        verify(savedPostRepository).deleteByPost(post);
        verify(postRepository).delete(post);
    }

    @Test
    void deletePostAliasRouteAlsoRemovesPostAndRelatedRecords() throws Exception {
        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setEmail(OWNER_EMAIL);
        owner.setRole(Role.USER);

        Post post = new Post();
        post.setId(POST_ID);
        post.setUser(owner);

        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        mockMvc.perform(post("/api/posts/{id}/delete", POST_ID)
                        .principal(new UsernamePasswordAuthenticationToken(
                                OWNER_EMAIL,
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.deletedId").value(POST_ID));

        verify(commentRepository).deleteByPost(post);
        verify(likeRepository).deleteByPost(post);
        verify(savedPostRepository).deleteByPost(post);
        verify(postRepository).delete(post);
    }
}
