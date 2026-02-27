package com.socialsea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SocialseaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialseaApplication.class, args);
    }
}
