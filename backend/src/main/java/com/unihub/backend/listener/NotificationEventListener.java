package com.unihub.backend.listener;

import com.unihub.backend.event.CsvSyncCompletedEvent;
import com.unihub.backend.event.WorkshopCancelledEvent;
import com.unihub.backend.event.WorkshopRegistrationSuccessEvent;
import com.unihub.backend.event.WorkshopReminderEvent;
import com.unihub.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationSuccess(WorkshopRegistrationSuccessEvent event) {
        notificationService.handleRegistrationSuccess(event);
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkshopCancelled(WorkshopCancelledEvent event) {
        notificationService.handleWorkshopCancelled(event);
    }

    @Async("notificationTaskExecutor")
    @EventListener
    public void onWorkshopReminder(WorkshopReminderEvent event) {
        notificationService.handleWorkshopReminder(event);
    }

    @Async("notificationTaskExecutor")
    @EventListener
    public void onCsvSyncCompleted(CsvSyncCompletedEvent event) {
        notificationService.handleCsvSyncCompleted(event);
    }
}
