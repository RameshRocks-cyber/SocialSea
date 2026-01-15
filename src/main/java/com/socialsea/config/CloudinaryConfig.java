package com.socialsea.config;

import com.cloudinary.Cloudinary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Bean
    @ConditionalOnProperty(name = {"cloudinary.cloud-name", "cloudinary.api-key", "cloudinary.api-secret"})
    public Cloudinary cloudinary(Environment env) {
        String cloudName = env.getProperty("cloudinary.cloud-name");
        String apiKey = env.getProperty("cloudinary.api-key");
        String apiSecret = env.getProperty("cloudinary.api-secret");

        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", true);

        return new Cloudinary(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public Cloudinary fallbackCloudinary() {
        // Provide a harmless fallback so autowiring doesn't fail when credentials are absent.
        return new Cloudinary(new HashMap<>());
    }

}
