package com.unihub.backend.infra;

import com.unihub.backend.service.SeatLockingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SeatLockingServiceTest extends RedisIntegrationTestBase {

    @Autowired
    private SeatLockingService seatLockingService;

    @Test
    void reserveSeatSucceedsAndCreatesHoldKey() {
        String workshopId = "ws-1";
        String userId = "user-1";

        seatLockingService.initSlots(workshopId, 1, Duration.ofHours(1).toSeconds());

        boolean reserved = seatLockingService.reserveSeat(workshopId, userId);
        Long ttl = redis.getExpire("hold:" + workshopId + ":" + userId, TimeUnit.SECONDS);

        assertThat(reserved).isTrue();
        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(1L, 600L);
    }

    @Test
    void reserveSeatFailsWhenNoSlots() {
        String workshopId = "ws-2";

        seatLockingService.initSlots(workshopId, 0, Duration.ofHours(1).toSeconds());

        boolean reserved = seatLockingService.reserveSeat(workshopId, "user-2");

        assertThat(reserved).isFalse();
        assertThat(seatLockingService.getRemainingSlots(workshopId)).isZero();
    }

    @Test
    void releaseSeatRestoresSlotsAndClearsHold() {
        String workshopId = "ws-3";
        String userId = "user-3";

        seatLockingService.initSlots(workshopId, 1, Duration.ofHours(1).toSeconds());
        seatLockingService.reserveSeat(workshopId, userId);

        seatLockingService.releaseSeat(workshopId, userId);

        assertThat(seatLockingService.getRemainingSlots(workshopId)).isEqualTo(1);
        assertThat(redis.hasKey("hold:" + workshopId + ":" + userId)).isFalse();
    }

    @Test
    void reserveSeatIsAtomicUnderConcurrency() throws InterruptedException {
        String workshopId = "ws-4";

        seatLockingService.initSlots(workshopId, 1, Duration.ofHours(1).toSeconds());

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();

        List<String> userIds = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            userIds.add("user-" + i);
        }

        for (String userId : userIds) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    if (seatLockingService.reserveSeat(workshopId, userId)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(seatLockingService.getRemainingSlots(workshopId)).isZero();
    }
}
