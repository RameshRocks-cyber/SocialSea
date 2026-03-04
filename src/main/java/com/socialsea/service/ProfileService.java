package com.socialsea.service;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepo;
    private final CloudinaryService cloudinaryService;
    private static final Pattern NAME_ALLOWED = Pattern.compile("^[a-z0-9._]{3,20}$");

    public Map<String, Object> setupProfile(Long userId, String name, String bio, MultipartFile pic) {
        User user = userRepo.findById(Objects.requireNonNull(userId, "userId"))
                .orElseThrow(() -> new RuntimeException("User not found"));

        String normalizedName = normalizeName(name);
        if (!NAME_ALLOWED.matcher(normalizedName).matches()) {
            throw new IllegalArgumentException("Username must be 3-20 chars: lowercase letters, numbers, dot, underscore");
        }

        if (userRepo.existsByNameIgnoreCaseAndIdNot(normalizedName, user.getId())) {
            throw new IllegalArgumentException("Username already taken");
        }

        user.setName(normalizedName);
        user.setBio(bio);

        if (pic != null && !pic.isEmpty()) {
            String url = cloudinaryService.upload(pic);
            user.setProfilePic(url);
        }

        user.setProfileCompleted(true);
        userRepo.save(user);

        return Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "bio", user.getBio() == null ? "" : user.getBio(),
                "profilePic", user.getProfilePic() == null ? "" : user.getProfilePic(),
                "profileCompleted", user.isProfileCompleted()
        );
    }

    public Map<String, Object> checkNameAvailability(String name, Long excludeUserId) {
        String normalized = normalizeName(name);
        boolean valid = NAME_ALLOWED.matcher(normalized).matches();
        boolean taken = normalized.isEmpty()
                || (excludeUserId == null
                ? userRepo.existsByNameIgnoreCase(normalized)
                : userRepo.existsByNameIgnoreCaseAndIdNot(normalized, excludeUserId));

        boolean available = valid && !taken;

        return Map.of(
                "name", normalized,
                "valid", valid,
                "available", available,
                "suggestions", available ? List.of() : suggestNames(normalized.isBlank() ? "user" : normalized, excludeUserId)
        );
    }

    private String normalizeName(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase().replaceAll("[^a-z0-9._]", "");
    }

    private List<String> suggestNames(String baseRaw, Long excludeUserId) {
        String base = normalizeName(baseRaw).replaceAll("[._]{2,}", "_");
        if (base.length() < 3) base = (base + "user").substring(0, 4);
        if (base.length() > 16) base = base.substring(0, 16);

        Set<String> unique = new LinkedHashSet<>();
        unique.add(base);
        unique.add(base + "._");
        unique.add(base + "_official");
        unique.add(base + "_real");

        int seed = Math.abs(base.hashCode());
        for (int i = 0; i < 18; i++) {
            int n = 100 + ((seed + (i * 137)) % 9000);
            unique.add(base + n);
            unique.add(base + "_" + n);
            if (unique.size() >= 24) break;
        }

        List<String> suggestions = new ArrayList<>();
        for (String candidate : unique) {
            if (!NAME_ALLOWED.matcher(candidate).matches()) continue;
            boolean exists = excludeUserId == null
                    ? userRepo.existsByNameIgnoreCase(candidate)
                    : userRepo.existsByNameIgnoreCaseAndIdNot(candidate, excludeUserId);
            if (!exists) suggestions.add(candidate);
            if (suggestions.size() >= 6) break;
        }
        return suggestions;
    }
}
