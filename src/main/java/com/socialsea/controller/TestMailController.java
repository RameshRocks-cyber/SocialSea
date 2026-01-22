package com.socialsea.controller;

import com.socialsea.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mail")
public class TestMailController {

    @Autowired
    private EmailService emailService;

    @GetMapping("/send")
    public String sendTestMail() {
        emailService.sendEmail(
                "receiver@gmail.com",
                "SocialSea Test Mail",
                "✅ Gmail App Password working successfully!"
        );
        return "Email sent successfully";
    }
}
