package com.socialsea.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sess -> 
                sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth

                // ✅ LOGIN MUST BE PUBLIC (VERY IMPORTANT)
                .requestMatchers(
                    "/admin/login",
                    "/auth/admin/login",
                    "/api/auth/admin/login"
                ).permitAll()

                // ✅ PUBLIC APIs (Reports, Anonymous Feed, etc.)
                .requestMatchers(
                    "/",
                    "/health",
                    "/public/**",
                    "/anonymous/**",
                    "/api/anonymous/**",
                    "/anonymous-upload/**",
                    "/anonymous/posts/**",
                    "/uploads/**",
                    "/api/public/**",
                    "/h2-console/**",
                    "/api/auth/**",
                    "/auth/**",
                    "/posts/**",
                    "/api/notifications/**"
                ).permitAll()

                // 🔒 ADMIN APIs
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
