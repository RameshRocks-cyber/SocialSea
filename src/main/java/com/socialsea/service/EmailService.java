package com.socialsea.service;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.Duration;

@Service
public class EmailService {

    @Value("${RESEND_API_KEY:}")
    private String apiKey;

    @Autowired(required = false)
    private JavaMailSender javaMailSender;

    private static final String FROM = "SocialSea <no-reply@socialsea.co.in>";

    private static final String RESEND_URL = "https://api.resend.com/emails";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(4))
            .readTimeout(Duration.ofSeconds(6))
            .writeTimeout(Duration.ofSeconds(6))
            .callTimeout(Duration.ofSeconds(8))
            .build();

    public void sendOtpEmail(String to, String otp) {
        String subject = "Your SocialSea OTP";
        String text = "Your OTP is: " + otp + "\n\nThis OTP expires in 5 minutes.";

        if (hasResend()) {
            String json = """
            {
              "from": "%s",
              "to": ["%s"],
              "subject": "%s",
              "html": "<h2>Your OTP</h2><p><b>%s</b></p>"
            }
            """.formatted(FROM, to, subject, otp);
            sendRequest(json);
            return;
        }

        sendViaSmtp(to, subject, text);
    }

    public void send(String to, String subject, String body) {
        if (hasResend()) {
            String json = """
            {
              "from": "%s",
              "to": ["%s"],
              "subject": "%s",
              "html": "<p>%s</p>"
            }
            """.formatted(FROM, to, subject, body);
            sendRequest(json);
            return;
        }
        sendViaSmtp(to, subject, body);
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

    private boolean hasResend() {
        return apiKey != null && !apiKey.isBlank();
    }


    private void sendViaSmtp(String to, String subject, String body) {
        if (javaMailSender == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email service not configured");
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            javaMailSender.send(msg);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMTP email send failed", ex);
        }
    }
}

