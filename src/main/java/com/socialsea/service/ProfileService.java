package com.socialsea.service;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepo;
    private final CloudinaryService cloudinaryService;

    public void setupProfile(Long userId, String name, String bio, MultipartFile pic) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(name);
        user.setBio(bio);

        if (pic != null && !pic.isEmpty()) {
            String url = cloudinaryService.upload(pic);
            user.setProfilePic(url);
        }

        user.setProfileCompleted(true);
        userRepo.save(user);
    }
}
