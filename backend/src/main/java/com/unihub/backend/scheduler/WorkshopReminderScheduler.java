package com.unihub.backend.scheduler;

import com.unihub.backend.dto.NotificationRecipient;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.event.WorkshopReminderEvent;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkshopReminderScheduler {

    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 0 18 * * *")
    @Transactional(readOnly = true)
    public void sendDailyReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime start = LocalDateTime.of(tomorrow, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(tomorrow, LocalTime.MAX);

        List<Workshop> workshops = workshopRepository.findByStatusAndStartTimeBetween("PUBLISHED", start, end);
        if (workshops.isEmpty()) {
            return;
        }

        for (Workshop workshop : workshops) {
            List<NotificationRecipient> recipients =
                    registrationRepository.findRecipientsByWorkshopIdAndStatus(workshop.getId(), "SUCCESS");

            if (recipients.isEmpty()) {
                continue;
            }

            eventPublisher.publishEvent(new WorkshopReminderEvent(
                    workshop.getId(),
                    workshop.getTitle(),
                    workshop.getStartTime(),
                    workshop.getRoom() != null ? workshop.getRoom().getName() : "TBD",
                    workshop.getRoom() != null ? workshop.getRoom().getLayoutMapUrl() : null,
                    recipients
            ));
        }

        log.info("Workshop reminder job completed for {} workshop(s)", workshops.size());
    }
}
