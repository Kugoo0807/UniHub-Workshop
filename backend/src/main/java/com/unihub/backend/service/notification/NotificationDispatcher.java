package com.unihub.backend.service.notification;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.entity.Notification;
import com.unihub.backend.entity.User;
import com.unihub.backend.repository.NotificationRepository;
import com.unihub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final List<NotificationStrategy> strategies;
    @Qualifier("notificationTaskExecutor")
    private final TaskExecutor notificationTaskExecutor;

    public void dispatch(NotificationRecipient recipient, NotificationContent content, List<String> channels) {
        Map<String, NotificationStrategy> strategyMap = resolveStrategies();
        User user = resolveUser(recipient.userId());
        List<Notification> pending = new ArrayList<>();

        for (String channel : channels) {
            NotificationStrategy strategy = strategyMap.get(channel);
            if (strategy == null) {
                continue;
            }

            Notification notification = Notification.builder()
                    .user(user)
                    .title(content.title())
                    .contentHtml(content.contentHtml())
                    .channel(channel)
                    .status("PENDING")
                    .retryCount(0)
                    .build();
            pending.add(notification);
        }

        if (pending.isEmpty()) {
            return;
        }

        List<Notification> saved = notificationRepository.saveAll(pending);
        for (Notification notification : saved) {
            NotificationStrategy strategy = strategyMap.get(notification.getChannel());
            if (strategy == null) {
                continue;
            }
            notificationTaskExecutor.execute(() -> strategy.send(notification, recipient, content));
        }
    }

    private Map<String, NotificationStrategy> resolveStrategies() {
        Map<String, NotificationStrategy> map = new HashMap<>();
        for (NotificationStrategy strategy : strategies) {
            map.put(strategy.getChannel(), strategy);
        }
        return map;
    }

    private User resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }
}
