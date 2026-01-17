package com.socialsea.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MailTestController {

    @Autowired
    private JavaMailSender mailSender;

    @GetMapping("/mail-test")
    public String testMail() {
        return mailSender == null ? "MAIL SENDER NULL ❌" : "MAIL SENDER OK ✅";
    }
}