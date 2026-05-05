package com.unihub.backend.scheduler;

import com.unihub.backend.service.SeatLockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Scheduled fallback for hold key expiry cleanup.
 *
 * This runs when Redis Keyspace Notifications are NOT enabled on the server,
 * ensuring that expired hold keys still trigger seat release.
 *
 * When keyspace listener IS active, this acts as a safety-net backup scan
 * every 5 minutes to catch any missed expirations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldKeyCleanupScheduler {

    private static final String HOLD_KEY_PATTERN = "hold:*";

    private final StringRedisTemplate redis;
    private final SeatLockingService seatLockingService;

    /**
     * Scan for hold keys every 5 minutes.
     * Keys that have no TTL (somehow persisted without expire) are cleaned up.
     * Expired keys are handled by the Keyspace Listener; this catches stragglers.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanupStaleHoldKeys() {
        Set<String> keysWithoutTTL = new HashSet<>();

        try (Cursor<String> cursor = redis.scan(
                ScanOptions.scanOptions().match(HOLD_KEY_PATTERN).count(100).build())) {

            while (cursor.hasNext()) {
                String key = cursor.next();
                Long ttl = redis.getExpire(key);
                if (ttl != null && ttl <= 0) {
                    keysWithoutTTL.add(key);
                }
            }
        }

        for (String key : keysWithoutTTL) {
            try {
                String payload = key.substring("hold:".length());
                int separatorIndex = payload.lastIndexOf(':');
                if (separatorIndex > 0 && separatorIndex < payload.length() - 1) {
                    String workshopId = payload.substring(0, separatorIndex);
                    String userId = payload.substring(separatorIndex + 1);
                    log.info("Cleanup: releasing expired hold for workshop={}, user={}", workshopId, userId);
                    seatLockingService.releaseSeat(workshopId, userId);
                }
            } catch (Exception e) {
                log.warn("Failed to cleanup hold key {}: {}", key, e.getMessage());
            }
        }

        if (!keysWithoutTTL.isEmpty()) {
            log.info("Cleaned up {} stale hold keys", keysWithoutTTL.size());
        }
    }
}
