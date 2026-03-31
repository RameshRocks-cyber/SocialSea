package com.socialsea.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "file:.env.local", ignoreResourceNotFound = true)
@PropertySource(value = "file:.env", ignoreResourceNotFound = true)
public class EnvFileConfig {
    // Loads local env files (KEY=VALUE) so Spring can resolve ${...} placeholders.
}
