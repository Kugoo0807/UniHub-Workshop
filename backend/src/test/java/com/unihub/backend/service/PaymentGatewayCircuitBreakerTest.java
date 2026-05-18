package com.unihub.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayCircuitBreakerTest {

    @Test
    void opensAfterFailureThreshold() {
        // The circuit should stay CLOSED until the configured number of consecutive failures is reached.
        MutableClock clock = new MutableClock();
        PaymentGatewayCircuitBreaker breaker = new PaymentGatewayCircuitBreaker(2, Duration.ofSeconds(30), clock);

        assertTrue(breaker.tryAcquirePermission());
        breaker.recordFailure();
        assertEquals(PaymentGatewayCircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.tryAcquirePermission());

        breaker.recordFailure();

        assertEquals(PaymentGatewayCircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.tryAcquirePermission());
    }

    @Test
    void transitionsToHalfOpenAfterCooldownAndClosesOnSuccess() {
        // After the open cooldown expires, one probe request is allowed in HALF_OPEN state.
        // A successful probe closes the circuit and normal traffic can resume.
        MutableClock clock = new MutableClock();
        PaymentGatewayCircuitBreaker breaker = new PaymentGatewayCircuitBreaker(1, Duration.ofSeconds(30), clock);

        breaker.recordFailure();
        assertFalse(breaker.tryAcquirePermission());

        clock.advance(Duration.ofSeconds(31));

        assertTrue(breaker.tryAcquirePermission());
        assertEquals(PaymentGatewayCircuitBreaker.State.HALF_OPEN, breaker.getState());
        assertFalse(breaker.tryAcquirePermission());

        breaker.recordSuccess();

        assertEquals(PaymentGatewayCircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.tryAcquirePermission());
    }

    @Test
    void halfOpenFailureReopensCircuit() {
        // If the HALF_OPEN probe fails, the circuit reopens and blocks new calls again.
        MutableClock clock = new MutableClock();
        PaymentGatewayCircuitBreaker breaker = new PaymentGatewayCircuitBreaker(1, Duration.ofSeconds(30), clock);

        breaker.recordFailure();
        clock.advance(Duration.ofSeconds(31));
        assertTrue(breaker.tryAcquirePermission());

        breaker.recordFailure();

        assertEquals(PaymentGatewayCircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.tryAcquirePermission());
    }

    private static class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-05-18T00:00:00Z");

        void advance(Duration duration) {
            // Move time forward without sleeping, so cooldown behavior is deterministic in tests.
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
