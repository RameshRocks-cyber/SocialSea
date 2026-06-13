package com.socialsea.util;

import com.socialsea.dto.PublicUserDto;
import com.socialsea.model.User;

public final class PublicUserPayloads {

    private PublicUserPayloads() {
    }

    public static String publicDisplayName(User user) {
        if (user == null) return "User";
        String raw = safeTrim(user.getName());
        return raw.isEmpty() ? "User" : raw;
    }

    public static String publicUsername(User user) {
        if (user == null) return "";
        return safeTrim(user.getName());
    }

    public static boolean isPubliclyVisible(User user) {
        return user != null && !user.isBanned();
    }

    public static PublicUserDto toUserSummary(User user, String profilePicUrl) {
        if (user == null) return null;
        return PublicUserDto.from(user, profilePicUrl);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
