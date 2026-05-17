package com.unihub.backend.service.notification;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.entity.Notification;
import com.unihub.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationStrategy implements NotificationStrategy {

    private static final long MIN_INTERVAL_MS = 34L;

    private static final Object RATE_LOCK = new Object();
    private static long lastSentAt = 0L;

    private final RestTemplate restTemplate;
    private final NotificationRepository notificationRepository;

    @Value("${notification.telegram.enabled:true}")
    private boolean telegramEnabled;

    @Value("${notification.telegram.token:}")
    private String telegramToken;

    @Override
    public String getChannel() {
        return "TELEGRAM";
    }

    @Override
    public void send(Notification notification, NotificationRecipient recipient, NotificationContent content) {
        if (!telegramEnabled) {
            markFailed(notification, "Telegram channel disabled");
            return;
        }

        if (telegramToken == null || telegramToken.isBlank()) {
            markFailed(notification, "Telegram token is not configured");
            return;
        }

        if (recipient == null || recipient.chatId() == null || recipient.chatId().isBlank()) {
            markFailed(notification, "Telegram chat_id is missing");
            return;
        }

        try {
            throttle();
            String endpoint = "https://api.telegram.org/bot" + telegramToken + "/sendMessage";
            // Preprocess: Convert HTML to Telegram's supported format and add newlines
            String textWithNewlines = content.contentHtml()
                .replaceAll("(?i)<br[^>]*>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)<hr[^>]*>", "\n---\n")
                .replaceAll("(?i)<h[1-6][^>]*>", "<b>")
                .replaceAll("(?i)</h[1-6]>", "</b>\n");

            // Safelist for Telegram's HTML parse mode
            Safelist allowedTags = Safelist.none()
                .addTags("b", "strong", "i", "em", "u", "s", "a", "code", "pre")
                .addAttributes("a", "href");

            String telegramMessage = Jsoup.clean(textWithNewlines, "", allowedTags, 
                    new Document.OutputSettings().prettyPrint(false));

            Map<String, Object> payload = Map.of(
                        "chat_id", recipient.chatId(),
                    "text", telegramMessage,
                    "parse_mode", "HTML"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Telegram API returned HTTP " + response.getStatusCode());
            }

            notification.setStatus("SUCCESS");
            notification.setErrorMessage(null);
            notificationRepository.save(notification);
        } catch (Exception ex) {
            markFailed(notification, ex.getMessage());
        }
    }

    private void throttle() {
        synchronized (RATE_LOCK) {
            long now = System.currentTimeMillis();
            long waitMs = MIN_INTERVAL_MS - (now - lastSentAt);
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            lastSentAt = System.currentTimeMillis();
        }
    }

    private void markFailed(Notification notification, String message) {
        notification.setStatus("FAILED");
        notification.setErrorMessage(message);
        Integer retryCount = notification.getRetryCount();
        notification.setRetryCount(retryCount == null ? 1 : retryCount + 1);
        notificationRepository.save(notification);
        log.warn("Telegram notification {} failed (Attempt {}): {}", notification.getId(), notification.getRetryCount(), message);
    }
}
