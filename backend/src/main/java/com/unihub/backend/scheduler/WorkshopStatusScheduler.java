package com.unihub.backend.scheduler;

import com.unihub.backend.entity.Workshop;
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
 * Scheduled job to automatically transition workshops from PUBLISHED to COMPLETED
 * once their end time has passed. This ensures the student's view remains clean
 * and Redis resources are released.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkshopStatusScheduler {

    private final WorkshopRepository workshopRepository;
    private final SeatLockingService seatLockingService;

    /**
     * Runs every hour to check for workshops that have ended.
     * Logic:
     * 1. Find workshops with status 'PUBLISHED' where end_time < now.
     * 2. Update status to 'COMPLETED'.
     * 3. Remove Redis slot keys to free memory.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void autoUpdateWorkshopStatus() {
        LocalDateTime now = LocalDateTime.now();
        
        // Query workshops that are still PUBLISHED but the event has finished
        List<Workshop> endedWorkshops = workshopRepository.findByStatusAndEndTimeBefore("PUBLISHED", now);
        
        if (endedWorkshops.isEmpty()) {
            return;
        }

        log.info("Found {} workshops to transition from PUBLISHED to COMPLETED", endedWorkshops.size());

        for (Workshop workshop : endedWorkshops) {
            try {
                // Update DB state
                workshop.setStatus("COMPLETED");
                workshopRepository.save(workshop);

                // Cleanup Infra resources
                // Since the workshop is over, we no longer need the slot counter in Redis
                seatLockingService.removeSlots(String.valueOf(workshop.getId()));
                
                log.info("Workshop ID: {} ({}) has been marked as COMPLETED.", workshop.getId(), workshop.getTitle());
            } catch (Exception e) {
                log.error("Error updating status for workshop {}: {}", workshop.getId(), e.getMessage());
            }
        }
    }
}
