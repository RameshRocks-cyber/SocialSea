package com.socialsea.config;

import com.cloudinary.Cloudinary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(Environment env) {
        String cloudName = env.getProperty("CLOUDINARY_CLOUD_NAME", "dummy");
        String apiKey = env.getProperty("CLOUDINARY_API_KEY", "dummy");
        String apiSecret = env.getProperty("CLOUDINARY_API_SECRET", "dummy");

        // Do not throw if credentials are missing — use safe dummy values so the app can start.
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", true);

        return new Cloudinary(config);
    }
}
