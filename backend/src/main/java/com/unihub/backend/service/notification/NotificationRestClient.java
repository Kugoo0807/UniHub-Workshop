package com.unihub.backend.service.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class NotificationRestClient {

    private final RestTemplate restTemplate;

    @Value("${notification.email.endpoint:}")
    private String emailEndpoint;

    @Value("${notification.telegram.endpoint:}")
    private String telegramEndpoint;

    public NotificationRestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendEmail(String toEmail, String subject, String html) {
        if (emailEndpoint == null || emailEndpoint.isBlank()) {
            throw new IllegalStateException("Email endpoint is not configured");
        }

        Map<String, Object> payload = Map.of(
                "to", toEmail,
                "subject", subject,
                "html", html
        );

        sendJson(emailEndpoint, payload);
    }

    public void sendTelegram(String phoneNumber, String messageHtml) {
        if (telegramEndpoint == null || telegramEndpoint.isBlank()) {
            throw new IllegalStateException("Telegram endpoint is not configured");
        }

        Map<String, Object> payload = Map.of(
                "phoneNumber", phoneNumber,
                "message", messageHtml
        );

        sendJson(telegramEndpoint, payload);
    }

    private void sendJson(String endpoint, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Notification gateway returned HTTP " + response.getStatusCode());
        }
    }
}
