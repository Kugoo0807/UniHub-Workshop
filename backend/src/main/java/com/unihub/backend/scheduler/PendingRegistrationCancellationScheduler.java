package com.unihub.backend.scheduler;

import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import com.unihub.backend.service.SeatLockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job to cancel stale PENDING registrations after the configured hold TTL
 * and release the reserved seat via SeatLockingService. This ensures DB state is
 * consistent when Redis hold keys expire or are lost.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingRegistrationCancellationScheduler {

    private static final int HOLD_MINUTES = 10;

    private final RegistrationRepository registrationRepository;
    private final SeatLockingService seatLockingService;
    private final WorkshopRepository workshopRepository;

    // Run every minute to keep timely cleanup (adjustable)
    @Scheduled(fixedRate = 60 * 1000)
    @Transactional
    public void cancelStalePendingRegistrations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(HOLD_MINUTES);
        List<Registration> stale = registrationRepository.findByStatusAndCreatedAtBefore("PENDING", cutoff);
        if (stale.isEmpty()) return;

        log.info("Found {} stale PENDING registrations to cancel", stale.size());

        for (Registration r : stale) {
            try {
                Long workshopId = r.getWorkshop().getId();
                Long userId = r.getUser().getId();

                r.setStatus("CANCELLED");
                registrationRepository.save(r);

                // Release seat in infra and increment remainingSlots in DB
                seatLockingService.releaseSeat(String.valueOf(workshopId), String.valueOf(userId));

                Workshop w = r.getWorkshop();
                if (w != null) {
                    Integer current = w.getRemainingSlots() != null ? w.getRemainingSlots() : 0;
                    int total = w.getTotalSlots() != null ? w.getTotalSlots() : current + 1;
                    w.setRemainingSlots(Math.min(total, current + 1));
                    workshopRepository.save(w);
                }

                log.info("Cancelled registration {} for workshop {} and released seat for user {}",
                        r.getId(), workshopId, userId);
            } catch (Exception ex) {
                log.warn("Failed to cancel stale registration {}: {}", r.getId(), ex.getMessage());
            }
        }
    }
}
