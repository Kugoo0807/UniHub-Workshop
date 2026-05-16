package com.unihub.backend.event;

import com.unihub.backend.dto.NotificationRecipient;

import java.time.LocalDateTime;
import java.util.List;

public record WorkshopReminderEvent(
        Long workshopId,
        String workshopTitle,
        LocalDateTime startTime,
        String roomName,
        String layoutMapUrl,
        List<NotificationRecipient> recipients
) {
}
