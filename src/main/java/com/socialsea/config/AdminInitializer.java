package com.socialsea.config;

import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class AdminInitializer {

    @Value("${app.admin.bootstrap.enabled:false}")
    private boolean adminBootstrapEnabled;

    @Value("${app.admin.bootstrap.email:}")
    private String adminBootstrapEmail;

    @Value("${app.admin.bootstrap.password:}")
    private String adminBootstrapPassword;

    @Bean
    CommandLineRunner initAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Environment env,
            JdbcTemplate jdbcTemplate
    ) {
        return args -> {
            System.out.println("ACTIVE PROFILES: " + java.util.Arrays.toString(env.getActiveProfiles()));
            ensureUserLocationColumns(jdbcTemplate);

            try {
                if (!adminBootstrapEnabled) {
                    return;
                }

                String adminEmail = adminBootstrapEmail != null ? adminBootstrapEmail.trim() : "";
                String adminPassword = adminBootstrapPassword != null ? adminBootstrapPassword.trim() : "";

                if (adminEmail.isBlank() || adminPassword.isBlank()) {
                    System.err.println("ADMIN INIT SKIPPED: set APP_ADMIN_BOOTSTRAP_EMAIL and APP_ADMIN_BOOTSTRAP_PASSWORD");
                    return;
                }

                if (userRepository.findByEmail(adminEmail).isEmpty()) {
                    User admin = new User();
                    admin.setEmail(adminEmail);
                    admin.setPassword(passwordEncoder.encode(adminPassword));
                    admin.setRole(Role.ADMIN);
                    admin.setBanned(false);

                    userRepository.save(admin);
                    System.out.println("DEFAULT ADMIN CREATED");
                } else {
                    System.out.println("ADMIN ALREADY EXISTS");
                }
            } catch (Exception e) {
                System.err.println("ADMIN INIT FAILED (Likely DB not ready): " + e.getMessage());
            }
        };
    }

    private void ensureUserLocationColumns(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_latitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_longitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS location_updated_at TIMESTAMP");
        } catch (Exception e) {
            System.err.println("USERS COLUMN PATCH FAILED: " + e.getMessage());
        }
    }
}
