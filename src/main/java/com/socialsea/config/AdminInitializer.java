package com.socialsea.config;

import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminInitializer {

    @Bean
    CommandLineRunner initAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder, Environment env) {
        return args -> {
            
            System.out.println("🚀 ACTIVE PROFILES: " + java.util.Arrays.toString(env.getActiveProfiles()));

            try {
                String adminEmail = "jekkaramesh788@gmail.com";

                if (userRepository.findByEmail(adminEmail).isEmpty()) {
                    User admin = new User();
                    admin.setEmail(adminEmail);
                    admin.setPassword(passwordEncoder.encode("Root@000"));
                    admin.setRole(Role.ADMIN);
                    admin.setBanned(false);

                    userRepository.save(admin);
                    System.out.println("✅ DEFAULT ADMIN CREATED");
                } else {
                    System.out.println("ℹ️ ADMIN ALREADY EXISTS");
                }
            } catch (Exception e) {
                System.err.println("⚠️ ADMIN INIT FAILED (Likely DB not ready): " + e.getMessage());
            }
        };
    }
}