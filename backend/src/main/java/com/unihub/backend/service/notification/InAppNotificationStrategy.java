package com.unihub.backend.service.notification;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.entity.Notification;
import com.unihub.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InAppNotificationStrategy implements NotificationStrategy {

    private final NotificationRepository notificationRepository;

    @Override
    public String getChannel() {
        return "IN_APP";
    }

    @Override
    public void send(Notification notification, NotificationRecipient recipient, NotificationContent content) {
        notification.setStatus("SUCCESS");
        notification.setErrorMessage(null);
        notificationRepository.save(notification);
    }
}
