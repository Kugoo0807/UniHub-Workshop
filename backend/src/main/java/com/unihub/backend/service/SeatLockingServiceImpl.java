package com.unihub.backend.service;

import com.unihub.backend.entity.Workshop;
import com.unihub.backend.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatLockingServiceImpl implements SeatLockingService {

    private static final String SLOTS_KEY_PREFIX = "workshop:";
    private static final String SLOTS_SUFFIX = ":slots";
    private static final String HOLD_KEY_PREFIX = "hold:";
    private static final Duration HOLD_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final WorkshopRepository workshopRepository;
    private final DefaultRedisScript<Long> reserveSeatScript;
    private final DefaultRedisScript<Long> releaseSeatScript;

    @Override
    public boolean reserveSeat(String workshopId, String userId) {
        String slotsKey = buildSlotsKey(workshopId);
        ensureSlotsInitialized(workshopId, slotsKey);
        Long result = redis.execute(reserveSeatScript, Collections.singletonList(slotsKey));

        if (result != null && result == 1L) {
            String holdKey = buildHoldKey(workshopId, userId);
            redis.opsForValue().set(holdKey, "1");
            log.debug("Seat reserved: workshop={}, user={}", workshopId, userId);
            return true;
        }
        log.debug("Seat reservation failed (no slots): workshop={}, user={}", workshopId, userId);
        return false;
    }

    @Override
    public void releaseSeat(String workshopId, String userId) {
        String holdKey = buildHoldKey(workshopId, userId);

        // Check if hold exists for this user (idempotent release)
        String holdValue = redis.opsForValue().getAndDelete(holdKey);
        if (holdValue == null) {
            log.debug("No hold key found for workshop={}, user={}; release skipped", workshopId, userId);
            return;
        }

        String slotsKey = buildSlotsKey(workshopId);
        String maxSlotsStr = redis.opsForValue().get(slotsKey + ":max");
        if (maxSlotsStr == null) {
            // If max key doesn't exist, just increment without ceiling
            redis.opsForValue().increment(slotsKey);
            log.debug("Seat released (no max cap): workshop={}, user={}", workshopId, userId);
            return;
        }

        redis.execute(releaseSeatScript, List.of(slotsKey, maxSlotsStr));
        log.debug("Seat released: workshop={}, user={}", workshopId, userId);
    }

    @Override
    public int getRemainingSlots(String workshopId) {
        String slotsKey = buildSlotsKey(workshopId);
        String value = redis.opsForValue().get(slotsKey);
        if (value == null) {
            return -1; // Signal: key doesn't exist → caller should fallback to DB
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid slots value in Redis for key={}: {}", slotsKey, value);
            return 0;
        }
    }

    @Override
    public void initSlots(String workshopId, int totalSlots, long ttlSeconds) {
        String slotsKey = buildSlotsKey(workshopId);
        String maxKey = slotsKey + ":max";

        redis.opsForValue().set(slotsKey, String.valueOf(totalSlots), Duration.ofSeconds(ttlSeconds));
        redis.opsForValue().set(maxKey, String.valueOf(totalSlots), Duration.ofSeconds(ttlSeconds));
        log.info("Initialized slots for workshop={}: total={}, TTL={}s", workshopId, totalSlots, ttlSeconds);
    }

    @Override
    public void updateSlots(String workshopId, int newTotalSlots, int oldTotalSlots) {
        String slotsKey = buildSlotsKey(workshopId);
        String maxKey = slotsKey + ":max";

        Boolean exists = redis.hasKey(slotsKey);
        if (Boolean.FALSE.equals(exists)) {
            log.warn("Cannot update slots for workshop={}: key not found in Redis", workshopId);
            return;
        }

        int delta = newTotalSlots - oldTotalSlots;
        if (delta > 0) {
            redis.opsForValue().increment(slotsKey, delta);
        } else if (delta < 0) {
            redis.opsForValue().decrement(slotsKey, -delta);
        }
        redis.opsForValue().set(maxKey, String.valueOf(newTotalSlots));

        log.info("Updated slots for workshop={}: newTotal={}, delta={}", workshopId, newTotalSlots, delta);
    }

    @Override
    public void removeSlots(String workshopId) {
        String slotsKey = buildSlotsKey(workshopId);
        String maxKey = slotsKey + ":max";
        redis.delete(List.of(slotsKey, maxKey));
        log.info("Removed slot keys for workshop={}", workshopId);
    }

    // ─── Key builders ───

    private String buildSlotsKey(String workshopId) {
        return SLOTS_KEY_PREFIX + workshopId + SLOTS_SUFFIX;
    }

    private String buildHoldKey(String workshopId, String userId) {
        return HOLD_KEY_PREFIX + workshopId + ":" + userId;
    }

    private void ensureSlotsInitialized(String workshopId, String slotsKey) {
        Boolean exists = redis.hasKey(slotsKey);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        workshopRepository.findById(Long.valueOf(workshopId)).ifPresent(workshop -> {
            int remainingSlots = workshop.getRemainingSlots() != null
                    ? workshop.getRemainingSlots()
                    : workshop.getTotalSlots();
            String maxKey = slotsKey + ":max";
            redis.opsForValue().set(slotsKey, String.valueOf(remainingSlots));
            redis.opsForValue().set(maxKey, String.valueOf(workshop.getTotalSlots()));
            log.warn("Recovered missing Redis slots for workshop={} from DB: remaining={}",
                    workshopId, remainingSlots);
        });
    }
}