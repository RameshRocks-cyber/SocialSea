package com.socialsea.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

@Service
public class GmailService {

    private Gmail gmail;

    public GmailService() {
        try {
            String base64Creds = System.getenv("GMAIL_CREDENTIALS_BASE64");

            if (base64Creds == null || base64Creds.isEmpty()) {
                throw new RuntimeException("GMAIL_CREDENTIALS_BASE64 not set");
            }

            byte[] decoded = Base64.getDecoder().decode(base64Creds);
            ByteArrayInputStream credStream = new ByteArrayInputStream(decoded);

            GoogleCredential credential = GoogleCredential.fromStream(credStream)
                    .createScoped(Collections.singleton("https://www.googleapis.com/auth/gmail.send"));

            gmail = new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    credential
            ).setApplicationName("SocialSea").build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Gmail API", e);
        }
    }

    public void sendOtp(String to, String otp) {
        try {
            String from = System.getenv("GMAIL_SENDER");

            String email =
                    "From: " + from + "\n" +
                    "To: " + to + "\n" +
                    "Subject: Your SocialSea OTP\n\n" +
                    "Your OTP is: " + otp + "\n\n" +
                    "Valid for 5 minutes.";

            Message message = new Message();
            message.setRaw(
                    Base64.getUrlEncoder()
                            .encodeToString(email.getBytes(StandardCharsets.UTF_8))
            );

            gmail.users().messages().send("me", message).execute();

        } catch (Exception e) {
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }
}
