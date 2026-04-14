package com.socialsea.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.security.allowed-origins:*}")
    private String allowedOriginsCsv;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        String[] originPatterns = Arrays.stream(String.valueOf(allowedOriginsCsv).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toArray(String[]::new);

        if (originPatterns.length == 0) {
            originPatterns = new String[]{"*"};
        }

        registry.addMapping("/**")
                .allowedOriginPatterns(originPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(originPatterns.length != 1 || !"*".equals(originPatterns[0]));
    }
}
