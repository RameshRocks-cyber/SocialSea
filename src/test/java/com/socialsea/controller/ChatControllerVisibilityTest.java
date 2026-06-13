package com.socialsea.controller;

import com.socialsea.model.User;
import com.socialsea.repository.ChatMessageRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.PresenceService;
import com.socialsea.service.UploadService;
import com.socialsea.service.WebPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerVisibilityTest {

    @InjectMocks
    private ChatController controller;

    @Mock
    private UserRepository userRepo;

    @Mock
    private ChatMessageRepository chatRepo;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UploadService uploadService;

    @Mock
    private PresenceService presenceService;

    @Mock
    private WebPushService webPushService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addPlaceholderValue("app.security.allowed-origins", "*")
                .build();
    }

    @Test
    void hiddenDirectChatUserIsNotResolvable() throws Exception {
        User viewer = user(1L, "viewer@example.com", "viewer", false);
        User hidden = user(2L, "michel@example.com", "michel", true);
        hidden.setBanned(true);

        when(userRepo.findByEmailIgnoreCase(viewer.getEmail())).thenReturn(Optional.of(viewer));
        when(userRepo.findById(hidden.getId())).thenReturn(Optional.of(hidden));

        mockMvc.perform(get("/api/chat/{otherUserId}/messages", hidden.getId())
                        .principal(auth(viewer)))
                .andExpect(status().isNotFound());

        verifyNoInteractions(chatRepo);
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
