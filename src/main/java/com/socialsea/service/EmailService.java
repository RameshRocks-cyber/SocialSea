package com.socialsea.service;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class EmailService {

    @Value("${RESEND_API_KEY}")
    private String apiKey;

    @Value("${MAIL_FROM}")
    private String from;

    private static final String RESEND_URL = "https://api.resend.com/emails";

    @Async
    public void sendOtpEmail(String to, String otp) {

        String json = """
        {
          "from": "%s",
          "to": ["%s"],
          "subject": "Your SocialSea OTP",
          "html": "<h2>Your OTP</h2><p><b>%s</b></p>"
        }
        """.formatted(from, to, otp);

        sendRequest(json);
    }

    public void send(String to, String subject, String body) {
        String json = """
        {
          "from": "%s",
          "to": ["%s"],
          "subject": "%s",
          "html": "<p>%s</p>"
        }
        """.formatted(from, to, subject, body);

        sendRequest(json);
    }

    public void sendEmail(String to, String subject, String body) {
        send(to, subject, body);
    }

    private void sendRequest(String json) {
        Request request = new Request.Builder()
                .url(RESEND_URL)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        OkHttpClient client = new OkHttpClient();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Email failed: " + response.body().string());
            }
        } catch (IOException e) {
            throw new RuntimeException("Email send error", e);
        }
    }
}
