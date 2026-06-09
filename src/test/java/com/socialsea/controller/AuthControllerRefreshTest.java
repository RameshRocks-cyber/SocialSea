package com.socialsea.controller;

import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.security.AuthCookieUtil;
import com.socialsea.security.JwtUtil;
import com.socialsea.service.LoginSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.http.Cookie;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshTest {

    private static final String REFRESH_TOKEN = "refresh.jwt.token";
    private static final String USER_EMAIL = "alice@example.com";
    private static final long USER_ID = 42L;
    private static final String SESSION_ID = "session-123";

    @InjectMocks
    private AuthController controller;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginSessionService loginSessionService;

    @Mock
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "requireHttps", false);
        ReflectionTestUtils.setField(controller, "refreshTokenMaxAgeMs", 2_592_000_000L);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void refreshReadsRefreshCookieAndOmitsRefreshTokenFromJsonBody() throws Exception {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(USER_EMAIL);
        user.setRole(Role.USER);

        when(jwtUtil.isExpired(REFRESH_TOKEN)).thenReturn(false);
        when(jwtUtil.isRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(jwtUtil.extractUsername(REFRESH_TOKEN)).thenReturn(USER_EMAIL);
        when(jwtUtil.extractTokenId(REFRESH_TOKEN)).thenReturn(SESSION_ID);
        when(userRepository.findByEmailIgnoreCase(USER_EMAIL)).thenReturn(Optional.of(user));
        when(loginSessionService.isActiveSession(USER_ID, SESSION_ID)).thenReturn(true);
        when(jwtUtil.generateAccessToken(USER_EMAIL, SESSION_ID)).thenReturn("new.access.token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(new Cookie(AuthCookieUtil.REFRESH_TOKEN_COOKIE_NAME, REFRESH_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").doesNotExist())
                .andExpect(jsonPath("$.role").value(Role.USER.name()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(result -> {
                    var cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    assertTrue(cookies.stream().anyMatch(v -> v.contains(AuthCookieUtil.ACCESS_TOKEN_COOKIE_NAME + "=")));
                    assertTrue(cookies.stream().anyMatch(v -> v.contains(AuthCookieUtil.REFRESH_TOKEN_COOKIE_NAME + "=" + REFRESH_TOKEN)));
                    assertTrue(cookies.stream().anyMatch(v -> v.contains("HttpOnly")));
                    assertTrue(cookies.stream().anyMatch(v -> v.contains("Path=/api")));
                });

        verify(loginSessionService).isActiveSession(USER_ID, SESSION_ID);
        verify(loginSessionService).touch(SESSION_ID);
    }

    @Test
    void refreshClearsRefreshCookieWhenTokenIsInvalid() throws Exception {
        when(jwtUtil.isExpired(REFRESH_TOKEN)).thenThrow(new RuntimeException("bad token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(new Cookie(AuthCookieUtil.REFRESH_TOKEN_COOKIE_NAME, REFRESH_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Refresh token invalid"))
                .andExpect(result -> {
                    var cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    assertTrue(cookies.stream().anyMatch(v -> v.contains(AuthCookieUtil.ACCESS_TOKEN_COOKIE_NAME + "=")));
                    assertTrue(cookies.stream().anyMatch(v -> v.contains(AuthCookieUtil.REFRESH_TOKEN_COOKIE_NAME + "=")));
                    assertTrue(cookies.stream().anyMatch(v -> v.contains("Max-Age=0")));
                });

        verify(jwtUtil).isExpired(REFRESH_TOKEN);
        verify(jwtUtil, never()).isRefreshToken(anyString());
        verifyNoInteractions(userRepository, loginSessionService);
    }
}
