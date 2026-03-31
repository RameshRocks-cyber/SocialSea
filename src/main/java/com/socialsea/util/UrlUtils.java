package com.socialsea.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public final class UrlUtils {

    private UrlUtils() {}

    public static String toAbsoluteUrl(HttpServletRequest request, String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return trimmed;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (request == null) {
            return trimmed;
        }

        String base = ServletUriComponentsBuilder.fromRequest(request)
                .replacePath(request.getContextPath())
                .replaceQuery(null)
                .build()
                .toUriString();

        boolean baseEndsWithSlash = base.endsWith("/");
        boolean pathStartsWithSlash = trimmed.startsWith("/");
        if (baseEndsWithSlash && pathStartsWithSlash) {
            return base.substring(0, base.length() - 1) + trimmed;
        }
        if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return base + "/" + trimmed;
        }
        return base + trimmed;
    }
}
