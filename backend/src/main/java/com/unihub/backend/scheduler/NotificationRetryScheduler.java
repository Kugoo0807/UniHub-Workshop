package com.unihub.backend.scheduler;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.entity.Notification;
import com.unihub.backend.entity.User;
import com.unihub.backend.repository.NotificationRepository;
import com.unihub.backend.service.notification.NotificationContent;
import com.unihub.backend.service.notification.NotificationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryScheduler {

    private static final int MAX_RETRIES = 3;

    private final NotificationRepository notificationRepository;
    private final List<NotificationStrategy> strategies;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = notificationRepository.findByStatusAndRetryCountLessThan("FAILED", MAX_RETRIES);

        if (failedNotifications.isEmpty()) {
            return;
        }

        log.info("Found {} failed notifications to retry.", failedNotifications.size());

        Map<String, NotificationStrategy> strategyMap = strategies.stream()
                .collect(Collectors.toMap(NotificationStrategy::getChannel, s -> s));

        for (Notification notification : failedNotifications) {
            NotificationStrategy strategy = strategyMap.get(notification.getChannel());
            if (strategy == null) {
                continue;
            }

            NotificationRecipient recipient = buildRecipient(notification.getUser());
            NotificationContent content = new NotificationContent(notification.getTitle(), notification.getContentHtml());

            log.info("Retrying notification ID {} via channel {}", notification.getId(), notification.getChannel());

            strategy.send(notification, recipient, content);
        }
    }

    private NotificationRecipient buildRecipient(User user) {
        if (user == null) {
            return new NotificationRecipient(null, "System", null, null);
        }
        return new NotificationRecipient(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber()
        );
    }
}