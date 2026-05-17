package com.unihub.backend.dto;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record NotificationResponse(
        Long id,
        String title,
        String contentHtml,
        String channel,
        String status,
        LocalDateTime createdAt
) {
}
