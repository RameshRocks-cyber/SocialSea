package com.socialsea.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public String health() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2) ? "OK" : "Database connection invalid";
        } catch (Exception e) {
            return "Database check failed: " + e.getMessage();
        }
    }
}
