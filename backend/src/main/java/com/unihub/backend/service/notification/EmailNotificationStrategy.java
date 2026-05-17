package com.unihub.backend.service.notification;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.entity.Notification;
import com.unihub.backend.repository.NotificationRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationStrategy implements NotificationStrategy {

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;

    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @Override
    public String getChannel() {
        return "EMAIL";
    }

    @Override
    public void send(Notification notification, NotificationRecipient recipient, NotificationContent content) {
        if (!emailEnabled) {
            markFailed(notification, "Email channel disabled");
            return;
        }

        if (recipient == null || recipient.email() == null || recipient.email().isBlank()) {
            markFailed(notification, "Recipient email is missing");
            return;
        }

        if (senderEmail == null || senderEmail.isBlank()) {
            markFailed(notification, "Sender email is not configured");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(recipient.email());
            helper.setSubject(content.title());
            helper.setText(content.contentHtml(), true);
            mailSender.send(message);

            notification.setStatus("SUCCESS");
            notification.setErrorMessage(null);
            notificationRepository.save(notification);
        } catch (Exception ex) {
            markFailed(notification, ex.getMessage());
        }
    }

    private void markFailed(Notification notification, String message) {
        notification.setStatus("FAILED");
        notification.setErrorMessage(message);
        Integer retryCount = notification.getRetryCount();
        notification.setRetryCount(retryCount == null ? 1 : retryCount + 1);
        notificationRepository.save(notification);
        log.warn("Email notification {} failed (Attempt {}): {}",
                notification.getId(), notification.getRetryCount(), message);
    }
}
