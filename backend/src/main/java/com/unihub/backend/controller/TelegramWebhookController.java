package com.unihub.backend.controller;

import com.unihub.backend.dto.TelegramWebhookRequest;
import com.unihub.backend.service.TelegramWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
@Tag(name = "Telegram", description = "Telegram webhook endpoints")
public class TelegramWebhookController {

    private final TelegramWebhookService telegramWebhookService;

    @PostMapping("/webhook")
    @Operation(summary = "Handle Telegram webhook", description = "Receives Telegram updates for account linking.")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody TelegramWebhookRequest payload,
            @RequestParam(value = "token", required = false) String token) {
        telegramWebhookService.handleWebhook(payload, token);
        return ResponseEntity.ok().build();
    }
}
