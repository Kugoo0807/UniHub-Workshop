package com.unihub.backend.service;

import com.unihub.backend.entity.Workshop;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class SeatRecoveryService {

    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String SLOT_KEY_FMT = "workshop:%d:slots";

    @PostConstruct
    public void initRedisCounters() {
        log.info("Initializing Redis seat counters from DB...");
        syncAllSafely();
    }

    // Run daily at 02:00
    @Scheduled(cron = "0 0 2 * * *")
    public void dailySync() {
        log.info("Running daily Redis <-> Postgres sync for seats");
        syncAllSafely();
    }

    // Run daily at 03:00 to cleanup ended workshops
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupEndedWorkshops() {
        log.info("Running cleanup for ended workshops in Redis");
        try {
            List<Workshop> workshops = workshopRepository.findAll();
            LocalDateTime now = LocalDateTime.now();
            for (Workshop w : workshops) {
                if (w.getEndTime().isBefore(now)) {
                    String key = String.format(SLOT_KEY_FMT, w.getId());
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                        redisTemplate.delete(key);
                        log.info("Cleaned up Redis key for ended workshop {}", w.getId());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Redis cleanup skipped due to unexpected error", ex);
        }
    }

    private void syncAllSafely() {
        try {
            syncAll();
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis seat sync skipped because Redis is unavailable: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("Redis seat sync skipped due to unexpected error", ex);
        }
    }

    public void syncAll() {
        List<Workshop> workshops = workshopRepository.findAll();
        for (Workshop w : workshops) {
            long sold = registrationRepository.countByWorkshopIdAndStatus(w.getId(), "SUCCESS");
            int remaining = Math.max(0, w.getTotalSlots() - (int) sold);
            String key = String.format(SLOT_KEY_FMT, w.getId());
            redisTemplate.opsForValue().set(key, String.valueOf(remaining));
            log.info("Synced workshop {}: remaining {}", w.getId(), remaining);
        }
    }
}
