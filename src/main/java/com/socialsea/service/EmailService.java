package com.socialsea.service;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;

@Service
public class EmailService {

    @Value("${RESEND_API_KEY:}")
    private String apiKey;

    private static final String FROM = "SocialSea <no-reply@socialsea.co.in>";

    private static final String RESEND_URL = "https://api.resend.com/emails";

    @Async
    public void sendOtpEmail(String to, String otp) {
        ensureConfigured();

        String json = """
        {
          "from": "%s",
          "to": ["%s"],
          "subject": "Your SocialSea OTP",
          "html": "<h2>Your OTP</h2><p><b>%s</b></p>"
        }
        """.formatted(FROM, to, otp);

        sendRequest(json);
    }

    public void send(String to, String subject, String body) {
        ensureConfigured();
        String json = """
        {
          "from": "%s",
          "to": ["%s"],
          "subject": "%s",
          "html": "<p>%s</p>"
        }
        """.formatted(FROM, to, subject, body);

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
                String responseBody = response.body() != null ? response.body().string() : "";
                String message = "Email service rejected the request (status="
                        + response.code()
                        + (responseBody.isBlank() ? "" : ", body=" + responseBody)
                        + ")";
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email service unavailable",
                    e
            );
        }
    }

    private void ensureConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email service not configured"
            );
        }
    }
}

