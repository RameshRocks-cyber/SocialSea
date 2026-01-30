package com.socialsea.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // ✅ PUBLIC / ANONYMOUS
                .requestMatchers(
                    "/",
                    "/health",
                    "/auth/**",
                    "/api/auth/**",
                    "/api/auth/refresh",
                    "/api/anonymous/**",     // 🔥 THIS IS THE KEY
                    "/api/public/**",
                    "/oauth2/**",      // 🔥 REQUIRED
                    "/login/**",
                    "/error"
                ).permitAll()

                // 🔐 ADMIN
                .requestMatchers("/api/admin/**", "/admin/**").authenticated()

                // 🔐 EVERYTHING ELSE
                .anyRequest().authenticated()
            )
            // ⚠️ UNCOMMENT BELOW AFTER ADDING spring-boot-starter-oauth2-client TO POM.XML
            // .oauth2Login(oauth -> oauth
            //     .defaultSuccessUrl("/oauth2/success", true)
            // )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
