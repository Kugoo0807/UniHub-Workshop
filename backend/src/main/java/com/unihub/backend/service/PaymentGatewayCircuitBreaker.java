package com.unihub.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class PaymentGatewayCircuitBreaker {

    enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean halfOpenProbeInProgress;

    // Production constructor with configuration properties
    @Autowired
    PaymentGatewayCircuitBreaker(@Value("${app.payment-gateway.circuit-breaker.failure-threshold:3}") int failureThreshold,
                                 @Value("${app.payment-gateway.circuit-breaker.open-duration-seconds:30}") long openDurationSeconds) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be at least 1");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofSeconds(openDurationSeconds);
        this.clock = Clock.systemUTC();
    }

    // Unit-test constructor with custom clock
    PaymentGatewayCircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    synchronized boolean tryAcquirePermission() {
        if (state == State.CLOSED) {
            return true;
        }

        if (state == State.OPEN) {
            if (openedAt != null && Instant.now(clock).isBefore(openedAt.plus(openDuration))) {
                return false;
            }

            state = State.HALF_OPEN;
            halfOpenProbeInProgress = true;
            return true;
        }

        if (halfOpenProbeInProgress) {
            return false;
        }

        halfOpenProbeInProgress = true;
        return true;
    }

    synchronized void recordSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        openedAt = null;
        halfOpenProbeInProgress = false;
    }

    synchronized void recordFailure() {
        halfOpenProbeInProgress = false;

        if (state == State.HALF_OPEN) {
            open();
            return;
        }

        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    synchronized State getState() {
        return state;
    }

    private void open() {
        state = State.OPEN;
        openedAt = Instant.now(clock);
        halfOpenProbeInProgress = false;
    }
}
