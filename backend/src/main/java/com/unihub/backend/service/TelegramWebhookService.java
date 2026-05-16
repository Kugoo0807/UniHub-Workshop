package com.unihub.backend.service;

import com.unihub.backend.dto.TelegramWebhookRequest;
import com.unihub.backend.entity.User;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.exception.UnauthorizedException;
import com.unihub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookService {

    private static final Pattern START_PATTERN = Pattern.compile("^/start\\s+user_(\\d+)$");

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${notification.telegram.token:}")
    private String telegramToken;

    @Value("${telegram.webhook.token:}")
    private String webhookToken;

    @Transactional
    public void handleWebhook(TelegramWebhookRequest request, String token) {
        validateWebhookToken(token);

        TelegramWebhookRequest.Message message = request == null ? null : request.message();
        if (message == null || message.text() == null || message.text().isBlank()) {
            throw new IllegalArgumentException("Invalid Telegram payload");
        }

        Long userId = parseUserId(message.text().trim());
        Long chatId = extractChatId(message);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setChatId(String.valueOf(chatId));
        userRepository.save(user);

        sendConfirmation(chatId);
        log.info("Telegram linked for user {} with chatId {}", userId, chatId);
    }

    private void validateWebhookToken(String token) {
        if (webhookToken == null || webhookToken.isBlank()) {
            return;
        }
        if (token == null || !webhookToken.equals(token)) {
            throw new UnauthorizedException("Invalid Telegram webhook token");
        }
    }

    private Long parseUserId(String text) {
        Matcher matcher = START_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Telegram start payload");
        }
        return Long.parseLong(matcher.group(1));
    }

    private Long extractChatId(TelegramWebhookRequest.Message message) {
        TelegramWebhookRequest.From from = message.from();
        if (from != null && from.id() != null) {
            return from.id();
        }
        TelegramWebhookRequest.Chat chat = message.chat();
        if (chat != null && chat.id() != null) {
            return chat.id();
        }
        throw new IllegalArgumentException("Telegram chat_id not found");
    }

    private void sendConfirmation(Long chatId) {
        if (telegramToken == null || telegramToken.isBlank()) {
            throw new IllegalArgumentException("Telegram token is not configured");
        }

        String endpoint = "https://api.telegram.org/bot" + telegramToken + "/sendMessage";
        Map<String, Object> payload = Map.of(
                "chat_id", String.valueOf(chatId),
                "text", "\u2705 Xin chao! Tai khoan UniHub Workshop cua ban da duoc lien ket thanh cong."
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Telegram API returned HTTP " + response.getStatusCode());
        }
    }
}
