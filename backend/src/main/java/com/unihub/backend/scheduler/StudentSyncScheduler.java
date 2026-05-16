package com.unihub.backend.scheduler;

import com.unihub.backend.service.StudentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StudentSyncScheduler {

    private final StudentSyncService studentSyncService;

    @Scheduled(cron = "${sync.students.cron:0 0 2 * * *}")
    public void runDailySync() {
        try {
            studentSyncService.syncStudents(false);
        } catch (Exception ex) {
            log.error("Student CSV sync failed: {}", ex.getMessage());
            // TODO: Notify admin via Telegram/Email when SYNC_FILE_NOT_FOUND
        }
    }
}
