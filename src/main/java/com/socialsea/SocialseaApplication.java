package com.socialsea;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication(
    exclude = {
        DataSourceAutoConfiguration.class
    }
)
public class SocialseaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialseaApplication.class, args);
    }

    @Bean
    CommandLineRunner passwordGenerator() {
        return args -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            System.out.println("\n==============================");
            System.out.println("ADMIN PASSWORD HASH:");
            System.out.println(encoder.encode("admin123"));
            System.out.println("==============================\n");
        };
    }
}
