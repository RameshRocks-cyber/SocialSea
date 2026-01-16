package com.socialsea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
public class SocialseaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialseaApplication.class, args);
    }
}
