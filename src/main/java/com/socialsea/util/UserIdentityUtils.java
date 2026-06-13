package com.socialsea.util;

import com.socialsea.model.User;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class UserIdentityUtils {

    private UserIdentityUtils() {
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    public static Optional<User> selectCanonicalUser(Collection<User> users) {
        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }

        return users.stream()
                .filter(Objects::nonNull)
                .min(canonicalUserComparator());
    }

    public static Comparator<User> canonicalUserComparator() {
        return Comparator
                .comparing(User::isBanned)
                .thenComparing(user -> hasPassword(user) ? 0 : 1)
                .thenComparing(UserIdentityUtils::createdAtRank)
                .thenComparing(UserIdentityUtils::idRank);
    }

    private static boolean hasPassword(User user) {
        return user != null && user.getPassword() != null && !user.getPassword().isBlank();
    }

    private static LocalDateTime createdAtRank(User user) {
        return user != null && user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.MAX;
    }

    private static Long idRank(User user) {
        return user != null && user.getId() != null ? user.getId() : Long.MAX_VALUE;
    }
}
