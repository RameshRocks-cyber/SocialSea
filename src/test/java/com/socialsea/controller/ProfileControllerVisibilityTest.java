package com.socialsea.controller;

import com.socialsea.model.User;
import com.socialsea.repository.CommentRepository;
import com.socialsea.repository.EmergencyAlertRepository;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.FollowRequestRepository;
import com.socialsea.repository.LikeRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.SavedPostRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerVisibilityTest {

    @InjectMocks
    private ProfileController controller;

    @Mock
    private UserRepository userRepo;

    @Mock
    private PostRepository postRepo;

    @Mock
    private FollowRepository followRepo;

    @Mock
    private FollowRequestRepository followRequestRepo;

    @Mock
    private LikeRepository likeRepo;

    @Mock
    private CommentRepository commentRepo;

    @Mock
    private SavedPostRepository savedPostRepo;

    @Mock
    private EmergencyAlertRepository emergencyRepo;

    @Mock
    private ProfileService profileService;

    @Mock
    private StoryRepository storyRepo;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void bannedProfileIsNotPubliclyVisible() throws Exception {
        User target = user(2L, "target@example.com", "target", true);
        target.setBanned(true);

        when(userRepo.findById(2L)).thenReturn(Optional.of(target));

        mockMvc.perform(get("/api/profile/2"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(postRepo, followRepo, followRequestRepo, likeRepo, commentRepo, savedPostRepo, emergencyRepo, profileService, storyRepo);
    }

    private static User user(Long id, String email, String name, boolean privateAccount) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setPrivateAccount(privateAccount);
        return user;
    }
}
