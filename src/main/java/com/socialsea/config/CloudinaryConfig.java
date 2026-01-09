package com.socialsea.config;

import com.cloudinary.Cloudinary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(Map.of(
                "cloud_name", System.getenv("CLOUDINARY_NAME"),
                "api_key", System.getenv("CLOUDINARY_KEY"),
                "api_secret", System.getenv("CLOUDINARY_SECRET")
        ));
    }
}
