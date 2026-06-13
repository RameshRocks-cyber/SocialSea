package com.socialsea.util;

import com.socialsea.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIdentityUtilsTest {

    @Test
    void normalizeEmailTrimsAndLowercases() {
        assertEquals("alice@example.com", UserIdentityUtils.normalizeEmail("  Alice@Example.com  "));
    }

    @Test
    void selectCanonicalUserPrefersActivePasswordedAccount() {
        User banned = user(3L, "alice@example.com", "secret", true, LocalDateTime.parse("2024-01-01T00:00:00"));
        User blankPassword = user(2L, "alice@example.com", "", false, LocalDateTime.parse("2024-01-02T00:00:00"));
        User active = user(1L, "alice@example.com", "secret", false, LocalDateTime.parse("2024-01-03T00:00:00"));

        var selected = UserIdentityUtils.selectCanonicalUser(java.util.List.of(banned, blankPassword, active));

        assertTrue(selected.isPresent());
        assertEquals(1L, selected.get().getId());
    }

    private static User user(Long id, String email, String password, boolean banned, LocalDateTime createdAt) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPassword(password);
        user.setBanned(banned);
        user.setCreatedAt(createdAt);
        return user;
    }
}
