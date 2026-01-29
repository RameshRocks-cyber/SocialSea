package com.socialsea.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void sendEmail(String to, String subject, String body) {
        log.info("📧 Attempting to send email to {}", to);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);

        log.info("✅ Email sent successfully to {}", to);
    }

    public void send(String to, String subject, String body) {
        sendEmail(to, subject, body);
    }

    // ✅ THIS IS THE MISSING METHOD
    @Async
    public void sendOtpEmail(String to, String otp) {
        String subject = "SocialSea - Email Verification OTP";
        String body = "Your OTP is: " + otp + "\n\nThis OTP will expire in 5 minutes.";
        sendEmail(to, subject, body);
    }
}
