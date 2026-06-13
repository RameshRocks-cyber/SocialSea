package com.socialsea.service;

import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.util.UserIdentityUtils;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getOrCreateVerifiedUser(String email) {
        String normalizedEmail = UserIdentityUtils.normalizeEmail(email);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Email is required");
        }
        return userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(normalizedEmail);
                    user.setRole(Role.USER);
                    return userRepository.save(user);
                });
    }

    public void banUser(String email) {
        String normalizedEmail = UserIdentityUtils.normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBanned(true);
        userRepository.save(user);
    }

    public void unbanUser(String email) {
        String normalizedEmail = UserIdentityUtils.normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBanned(false);
        userRepository.save(user);
    }
}
