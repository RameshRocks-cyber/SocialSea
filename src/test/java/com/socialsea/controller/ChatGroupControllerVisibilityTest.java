package com.socialsea.controller;

import com.socialsea.model.ChatGroup;
import com.socialsea.model.ChatGroupMember;
import com.socialsea.model.User;
import com.socialsea.repository.ChatGroupMemberRepository;
import com.socialsea.repository.ChatGroupRepository;
import com.socialsea.repository.ChatMessageRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatGroupControllerVisibilityTest {

    @InjectMocks
    private ChatGroupController controller;

    @Mock
    private UserRepository userRepo;

    @Mock
    private ChatMessageRepository chatRepo;

    @Mock
    private ChatGroupRepository groupRepo;

    @Mock
    private ChatGroupMemberRepository groupMemberRepo;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UploadService uploadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addPlaceholderValue("app.security.allowed-origins", "*")
                .build();
    }

    @Test
    void bannedGroupMemberIsHiddenFromGroupDetails() throws Exception {
        User viewer = user(1L, "viewer@example.com", "viewer", false);
        User visibleMember = user(2L, "friend@example.com", "friend", false);
        User hiddenMember = user(3L, "michel@example.com", "michel", true);
        hiddenMember.setBanned(true);

        ChatGroup group = group(10L, viewer);

        when(userRepo.findByEmailIgnoreCase(viewer.getEmail())).thenReturn(Optional.of(viewer));
        when(groupRepo.findByIdAndMembers_UserId(group.getId(), viewer.getId())).thenReturn(Optional.of(group));
        when(groupMemberRepo.findByGroupId(group.getId())).thenReturn(List.of(
                membership(group, viewer),
                membership(group, visibleMember),
                membership(group, hiddenMember)
        ));
        when(chatRepo.findByGroupIdOrderByCreatedAtDesc(group.getId(), org.springframework.data.domain.PageRequest.of(0, 1)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/chat/groups/{groupId}", group.getId())
                        .principal(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(content().string(not(containsString("michel"))));
    }

    private static User user(Long id, String email, String name, boolean privateAccount) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setPrivateAccount(privateAccount);
        return user;
    }

    private static ChatGroup group(Long id, User owner) {
        ChatGroup group = new ChatGroup();
        ReflectionTestUtils.setField(group, "id", id);
        group.setName("Group");
        group.setOwner(owner);
        return group;
    }

    private static ChatGroupMember membership(ChatGroup group, User user) {
        ChatGroupMember member = new ChatGroupMember();
        member.setGroup(group);
        member.setUser(user);
        return member;
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        return new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
