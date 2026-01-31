package com.socialsea.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void sendEmail(String to, String subject, String body) {
        log.info("📧 Attempting to send email to {}", to);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("✅ Email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to send email via SMTP", e);
            throw new RuntimeException(e);
        }
    }

    public void send(String to, String subject, String body) {
        sendEmail(to, subject, body);
    }

    @Async
    public void sendOtpEmail(String to, String otp) {
        String subject = "Your SocialSea OTP";
        String body = "Your OTP is: " + otp + "\n\nValid for 5 minutes.";
        sendEmail(to, subject, body);
    }
}
