package com.socialsea.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class EmailConfigCheck {

    @Bean
    public CommandLineRunner checkEmailConfig(Environment env) {
        return args -> {
            String resendKey = env.getProperty("RESEND_API_KEY");
            String mailFrom = env.getProperty("MAIL_FROM");

            if (resendKey == null || resendKey.isBlank()) {
                throw new IllegalStateException("❌ RESEND_API_KEY is missing. Set it in systemd env vars.");
            }

            if (mailFrom == null || mailFrom.isBlank()) {
                throw new IllegalStateException("❌ MAIL_FROM is missing. Set it in systemd env vars.");
            }

            System.out.println("✅ Email config loaded (Resend).");
        };
    }
}