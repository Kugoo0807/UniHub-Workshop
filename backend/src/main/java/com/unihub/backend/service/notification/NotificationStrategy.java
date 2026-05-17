package com.unihub.backend.service.notification;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.entity.Notification;

public interface NotificationStrategy {
    String getChannel();

    void send(Notification notification, NotificationRecipient recipient, NotificationContent content);
}
