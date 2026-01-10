package com.socialsea.config;

import java.net.URI;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        try {
            String explicit = env.getProperty("SPRING_DATASOURCE_URL");
            if (explicit != null && !explicit.isBlank()) {
                return DataSourceBuilder.create()
                        .url(explicit)
                        .username(env.getProperty("SPRING_DATASOURCE_USERNAME"))
                        .password(env.getProperty("SPRING_DATASOURCE_PASSWORD"))
                        .driverClassName("org.postgresql.Driver")
                        .build();
            }

            String databaseUrl = env.getProperty("DATABASE_URL");
            if (databaseUrl == null || databaseUrl.isBlank()) {
                return null;
            }

            if (databaseUrl.startsWith("jdbc:")) {
                return DataSourceBuilder.create()
                        .url(databaseUrl)
                        .username(env.getProperty("SPRING_DATASOURCE_USERNAME"))
                        .password(env.getProperty("SPRING_DATASOURCE_PASSWORD"))
                        .driverClassName("org.postgresql.Driver")
                        .build();
            }

            URI dbUri = new URI(databaseUrl);
            String userInfo = dbUri.getUserInfo();
            String username = null;
            String password = null;
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                if (parts.length > 1) password = parts[1];
            }

            String host = dbUri.getHost();
            int port = dbUri.getPort() == -1 ? 5432 : dbUri.getPort();
            String path = dbUri.getPath();
            String dbName = (path != null && path.startsWith("/")) ? path.substring(1) : path;

            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);

            DataSourceBuilder<?> builder = DataSourceBuilder.create()
                    .url(jdbcUrl)
                    .driverClassName("org.postgresql.Driver");
            if (username != null) builder.username(username);
            if (password != null) builder.password(password);

            return builder.build();
        } catch (Exception e) {
            return null;
        }
    }
}
