package com.unihub.backend.infra;

import com.unihub.backend.scheduler.HoldKeyCleanupScheduler;
import com.unihub.backend.service.SeatLockingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class HoldKeyCleanupSchedulerTest extends RedisIntegrationTestBase {

    @Autowired
    private HoldKeyCleanupScheduler scheduler;

    @Autowired
    private SeatLockingService seatLockingService;

    @Test
    void cleanupReleasesHoldKeysWithoutTtl() {
        String workshopId = "ws-cleanup";
        String userId = "user-cleanup";
        String slotsKey = "workshop:" + workshopId + ":slots";
        String maxKey = slotsKey + ":max";
        String holdKey = "hold:" + workshopId + ":" + userId;

        redis.opsForValue().set(slotsKey, "0");
        redis.opsForValue().set(maxKey, "1");
        redis.opsForValue().set(holdKey, "1");

        scheduler.cleanupStaleHoldKeys();

        assertThat(seatLockingService.getRemainingSlots(workshopId)).isEqualTo(1);
        assertThat(redis.hasKey(holdKey)).isFalse();
    }
}
