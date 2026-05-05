package com.unihub.backend.infra;

import com.unihub.backend.dto.IdempotencyResult;
import com.unihub.backend.enums.IdempotencyState;
import com.unihub.backend.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
    void markInFlightStoresStateWithTtl() {
        idempotencyService.markInFlight("in-flight", Duration.ofSeconds(5));

        IdempotencyState state = idempotencyService.getState("in-flight");
        Long ttl = redis.getExpire("payment:in-flight", TimeUnit.SECONDS);

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
}
