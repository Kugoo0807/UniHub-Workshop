package com.unihub.backend.service.notification;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.dto.NotificationResponse;
import com.unihub.backend.dto.PageResponse;
import com.unihub.backend.entity.Notification;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.event.CsvSyncCompletedEvent;
import com.unihub.backend.event.WorkshopCancelledEvent;
import com.unihub.backend.event.WorkshopRegistrationSuccessEvent;
import com.unihub.backend.event.WorkshopReminderEvent;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.NotificationRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final int CANCEL_BATCH_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final NotificationTemplateService templateService;
    private final NotificationDispatcher dispatcher;
    @Qualifier("notificationTaskExecutor")
    private final TaskExecutor notificationTaskExecutor;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getUserNotifications(Long userId, int page, int size) {
        Page<Notification> result = notificationRepository.findInAppNotificationsForUser(
                userId,
                "IN_APP",
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<NotificationResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<NotificationResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    public void handleRegistrationSuccess(WorkshopRegistrationSuccessEvent event) {
        Registration registration = registrationRepository.findByIdWithUserAndWorkshop(event.registrationId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        NotificationContent content = templateService.buildRegistrationSuccess(registration);
        NotificationRecipient recipient = new NotificationRecipient(
                registration.getUser().getId(),
                registration.getUser().getFullName(),
                registration.getUser().getEmail(),
            registration.getUser().getPhoneNumber(),
            registration.getUser().getChatId()
        );

        dispatchToRecipient(recipient, content, true);
    }

    public void handleWorkshopCancelled(WorkshopCancelledEvent event) {
        if (event.recipients() == null || event.recipients().isEmpty()) {
            return;
        }

        List<List<NotificationRecipient>> batches = partition(event.recipients(), CANCEL_BATCH_SIZE);
        for (List<NotificationRecipient> batch : batches) {
            notificationTaskExecutor.execute(() -> batch.forEach(recipient -> {
                NotificationContent content = templateService.buildWorkshopCancelled(
                        recipient,
                        event.workshopTitle(),
                        event.startTime(),
                        event.roomName(),
                        event.price()
                );
                dispatchToRecipient(recipient, content, true);
            }));
        }
    }

    public void handleWorkshopReminder(WorkshopReminderEvent event) {
        if (event.recipients() == null || event.recipients().isEmpty()) {
            return;
        }

        for (NotificationRecipient recipient : event.recipients()) {
            NotificationContent content = templateService.buildWorkshopReminder(
                    recipient,
                    event.workshopTitle(),
                    event.startTime(),
                    event.roomName(),
                    event.layoutMapUrl()
            );
            dispatchToRecipient(recipient, content, true);
        }
    }

    public void handleCsvSyncCompleted(CsvSyncCompletedEvent event) {
        List<NotificationRecipient> admins = userRepository.findRecipientsByRole("ADMIN");
        if (admins.isEmpty()) {
            log.warn("CSV sync completed but no admin users were found to notify.");
            return;
        }

        for (NotificationRecipient recipient : admins) {
            NotificationContent content = templateService.buildCsvSyncCompleted(
                    recipient,
                    event.totalRows(),
                    event.successRows(),
                    event.failedRows(),
                    event.durationSeconds()
            );
            dispatchToRecipient(recipient, content, false);
        }
    }

    private void dispatchToRecipient(NotificationRecipient recipient, NotificationContent content, boolean includeTelegram) {
        List<String> channels = new ArrayList<>();
        channels.add("EMAIL");
        channels.add("IN_APP");
        if (includeTelegram && isTelegramEligible(recipient.chatId())) {
            channels.add("TELEGRAM");
        }

        dispatcher.dispatch(recipient, content, channels);
    }

    private boolean isTelegramEligible(String chatId) {
        if (chatId == null) {
            return false;
        }
        String trimmed = chatId.trim();
        return !trimmed.isEmpty();
    }

    private List<List<NotificationRecipient>> partition(List<NotificationRecipient> items, int size) {
        List<List<NotificationRecipient>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            batches.add(items.subList(i, Math.min(i + size, items.size())));
        }
        return batches;
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .contentHtml(notification.getContentHtml())
                .channel(notification.getChannel())
                .status(notification.getStatus())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
