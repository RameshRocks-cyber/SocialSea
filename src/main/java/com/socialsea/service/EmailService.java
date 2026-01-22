package com.socialsea.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
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
