package com.socialsea.controller;

import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.FollowRequestRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FollowControllerPrivacyTest {

    @InjectMocks
    private FollowController controller;

    @Mock
    private FollowRepository followRepo;

    @Mock
    private FollowRequestRepository followRequestRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void privateFollowersListIsBlockedForNonFollowers() throws Exception {
        User viewer = user(1L, "viewer@example.com", "viewer", false);
        User target = user(2L, "target@example.com", "target", true);

        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));
        when(userRepo.findByNameIgnoreCase("target")).thenReturn(Optional.of(target));
        when(followRepo.existsByFollowerAndFollowing(viewer, target)).thenReturn(false);

        mockMvc.perform(get("/api/follow/target/followers/users")
                        .principal(auth(viewer)))
                .andExpect(status().isForbidden());

        verify(followRepo).existsByFollowerAndFollowing(viewer, target);
        verify(followRepo, never()).findByFollowing(target);
    }

    @Test
    void privateFollowingListIsBlockedForNonFollowers() throws Exception {
        User viewer = user(1L, "viewer@example.com", "viewer", false);
        User target = user(2L, "target@example.com", "target", true);

        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));
        when(userRepo.findByNameIgnoreCase("target")).thenReturn(Optional.of(target));
        when(followRepo.existsByFollowerAndFollowing(viewer, target)).thenReturn(false);

        mockMvc.perform(get("/api/follow/target/following/users")
                        .principal(auth(viewer)))
                .andExpect(status().isForbidden());

        verify(followRepo).existsByFollowerAndFollowing(viewer, target);
        verify(followRepo, never()).findByFollower(target);
    }

    @Test
    void bannedTargetIsHiddenFromFollowLists() throws Exception {
        User viewer = user(1L, "viewer@example.com", "viewer", false);
        User target = user(2L, "target@example.com", "target", true);
        target.setBanned(true);

        when(userRepo.findByEmail(viewer.getEmail())).thenReturn(Optional.of(viewer));
        when(userRepo.findByNameIgnoreCase("target")).thenReturn(Optional.of(target));

        mockMvc.perform(get("/api/follow/target/followers/users")
                        .principal(auth(viewer)))
                .andExpect(status().isNotFound());

        verifyNoInteractions(followRepo);
    }

    private static User user(Long id, String email, String name, boolean privateAccount) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setPrivateAccount(privateAccount);
        return user;
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        return new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
