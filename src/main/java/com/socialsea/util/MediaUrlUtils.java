package com.socialsea.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaUrlUtils {

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "m4v", "mov", "webm", "mkv", "avi", "mpeg", "mpg", "3gp", "ogv", "m3u8"
    );

    private static final Pattern EXTENSION_PATTERN = Pattern.compile("\\.([a-zA-Z0-9]{2,8})(?=($|[?#]))");
    private static final String CLOUDINARY_IMAGE_MARKER = "/image/upload/";
    private static final String CLOUDINARY_VIDEO_MARKER = "/video/upload/";
    private static final String CLOUDINARY_THUMBNAIL_TRANSFORM = "c_fill,g_auto,w_480,h_480,f_auto,q_auto";
    private static final String CLOUDINARY_MEDIUM_TRANSFORM = "c_limit,w_1280,f_auto,q_auto";

    private MediaUrlUtils() {}

    public static boolean isLikelyVideo(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) return false;
        String lowered = mediaUrl.trim().toLowerCase(Locale.ROOT);
        if (lowered.contains(CLOUDINARY_VIDEO_MARKER)) return true;

        Matcher matcher = EXTENSION_PATTERN.matcher(lowered);
        String extension = null;
        while (matcher.find()) {
            extension = matcher.group(1);
        }
        return extension != null && VIDEO_EXTENSIONS.contains(extension);
    }

    public static String thumbnailUrl(String mediaUrl, String mediaType) {
        if (mediaUrl == null || mediaUrl.isBlank()) return null;
        String trimmed = mediaUrl.trim();

        String normalizedType = normalizeMediaType(mediaType);
        boolean imageType = "image".equals(normalizedType);
        boolean videoType = "video".equals(normalizedType) || (!imageType && isLikelyVideo(trimmed));
        if (videoType) {
            return cloudinaryVideoThumbnail(trimmed);
        }

        if (!imageType) {
            return null;
        }

        String cloudinaryThumbnail = cloudinaryImageVariant(trimmed, CLOUDINARY_THUMBNAIL_TRANSFORM);
        return cloudinaryThumbnail != null ? cloudinaryThumbnail : trimmed;
    }

    public static String mediumUrl(String mediaUrl, String mediaType) {
        if (mediaUrl == null || mediaUrl.isBlank()) return null;
        String trimmed = mediaUrl.trim();

        String normalizedType = normalizeMediaType(mediaType);
        boolean imageType = "image".equals(normalizedType);
        boolean videoType = "video".equals(normalizedType) || (!imageType && isLikelyVideo(trimmed));
        if (videoType || !imageType) {
            return trimmed;
        }

        String cloudinaryMedium = cloudinaryImageVariant(trimmed, CLOUDINARY_MEDIUM_TRANSFORM);
        return cloudinaryMedium != null ? cloudinaryMedium : trimmed;
    }

    private static String normalizeMediaType(String mediaType) {
        if (mediaType == null) return "";
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }

    private static String cloudinaryVideoThumbnail(String mediaUrl) {
        int markerIndex = mediaUrl.indexOf(CLOUDINARY_VIDEO_MARKER);
        if (markerIndex < 0) return null;

        String withoutFragment = mediaUrl;
        String fragment = "";
        int hashIndex = withoutFragment.indexOf('#');
        if (hashIndex >= 0) {
            fragment = withoutFragment.substring(hashIndex);
            withoutFragment = withoutFragment.substring(0, hashIndex);
        }

        String withoutQuery = withoutFragment;
        String query = "";
        int queryIndex = withoutFragment.indexOf('?');
        if (queryIndex >= 0) {
            query = withoutFragment.substring(queryIndex);
            withoutQuery = withoutFragment.substring(0, queryIndex);
        }

        String prefix = withoutQuery.substring(0, markerIndex + CLOUDINARY_VIDEO_MARKER.length());
        String rest = withoutQuery.substring(markerIndex + CLOUDINARY_VIDEO_MARKER.length());
        if (rest.isBlank()) return null;

        String transformedRest = rest.startsWith("so_") ? rest : "so_1,f_jpg/" + rest;

        int lastSlash = transformedRest.lastIndexOf('/');
        String head = lastSlash >= 0 ? transformedRest.substring(0, lastSlash + 1) : "";
        String tail = lastSlash >= 0 ? transformedRest.substring(lastSlash + 1) : transformedRest;

        int lastDot = tail.lastIndexOf('.');
        if (lastDot > 0) {
            tail = tail.substring(0, lastDot);
        }

        return prefix + head + tail + ".jpg" + query + fragment;
    }

    private static String cloudinaryImageVariant(String mediaUrl, String transformation) {
        if (transformation == null || transformation.isBlank()) {
            return mediaUrl;
        }

        int markerIndex = mediaUrl.indexOf(CLOUDINARY_IMAGE_MARKER);
        if (markerIndex < 0) return null;

        String withoutFragment = mediaUrl;
        String fragment = "";
        int hashIndex = withoutFragment.indexOf('#');
        if (hashIndex >= 0) {
            fragment = withoutFragment.substring(hashIndex);
            withoutFragment = withoutFragment.substring(0, hashIndex);
        }

        String withoutQuery = withoutFragment;
        String query = "";
        int queryIndex = withoutFragment.indexOf('?');
        if (queryIndex >= 0) {
            query = withoutFragment.substring(queryIndex);
            withoutQuery = withoutFragment.substring(0, queryIndex);
        }

        String prefix = withoutQuery.substring(0, markerIndex + CLOUDINARY_IMAGE_MARKER.length());
        String rest = withoutQuery.substring(markerIndex + CLOUDINARY_IMAGE_MARKER.length());
        if (rest.isBlank()) return null;

        String transformedRest = rest.startsWith(transformation + "/") ? rest : transformation + "/" + rest;
        return prefix + transformedRest + query + fragment;
    }

}
