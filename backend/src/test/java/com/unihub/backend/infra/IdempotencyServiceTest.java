package com.unihub.backend.infra;

import com.unihub.backend.dto.IdempotencyResult;
import com.unihub.backend.enums.IdempotencyState;
import com.unihub.backend.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceTest extends RedisIntegrationTestBase {

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void getStateReturnsNotFoundWhenMissing() {
        IdempotencyState state = idempotencyService.getState("missing-key");

        assertThat(state).isEqualTo(IdempotencyState.NOT_FOUND);
    }

    @Test
    void tryMarkInFlightStoresStateWithTtlAndPreventsDuplicates() {
        // First attempt => TRUE
        boolean firstAttempt = idempotencyService.tryMarkInFlight("in-flight", Duration.ofSeconds(5));

        // Second attempt => FALSE (already in-flight)
        boolean secondAttempt = idempotencyService.tryMarkInFlight("in-flight", Duration.ofSeconds(5));

        IdempotencyState state = idempotencyService.getState("in-flight");
        Long ttl = redis.getExpire("payment:in-flight", TimeUnit.SECONDS);

        // Assertions
        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
        assertThat(state).isEqualTo(IdempotencyState.IN_FLIGHT);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(1L, 5L);
    }

    @Test
    void storeResultHandlesSuccessAndFailedStatesWithTtl() {
        IdempotencyResult success = IdempotencyResult.builder()
                .status("SUCCESS")
                .transactionId("tx-1")
                .message("ok")
                .build();

        idempotencyService.storeResult("pay-1", success, Duration.ofHours(24));

        IdempotencyState successState = idempotencyService.getState("pay-1");
        Long successTtl = redis.getExpire("payment:pay-1", TimeUnit.SECONDS);

        IdempotencyResult failed = IdempotencyResult.builder()
                .status("FAILED")
                .transactionId("tx-2")
                .message("failed")
                .build();

        idempotencyService.storeResult("pay-1", failed, Duration.ofHours(24));

        IdempotencyState failedState = idempotencyService.getState("pay-1");
        Long failedTtl = redis.getExpire("payment:pay-1", TimeUnit.SECONDS);

        assertThat(successState).isEqualTo(IdempotencyState.SUCCESS);
        assertThat(successTtl).isNotNull();
        assertThat(successTtl).isBetween(23 * 3600L, 24 * 3600L);
        assertThat(failedState).isEqualTo(IdempotencyState.FAILED);
        assertThat(failedTtl).isNotNull();
        assertThat(failedTtl).isBetween(23 * 3600L, 24 * 3600L);
    }

    @Test
    void tryMarkInFlightIsAtomicUnderConcurrency() throws InterruptedException {
        String idempotencyKey = "concurrent-pay-key";
        int threads = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    if (idempotencyService.tryMarkInFlight(idempotencyKey, Duration.ofMinutes(5))) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        // Wait until all threads are ready
        boolean isReady = ready.await(5, TimeUnit.SECONDS);
        assertThat(isReady).isTrue();

        start.countDown(); // Start all threads at the same time

        // Wait until all threads are done
        boolean isDone = done.await(5, TimeUnit.SECONDS);
        assertThat(isDone).isTrue();
        executor.shutdownNow();

        // Just one thread should succeed in marking the key as in-flight
        assertThat(successCount.get()).isEqualTo(1);

        assertThat(idempotencyService.getState(idempotencyKey)).isEqualTo(IdempotencyState.IN_FLIGHT);
    }
}
