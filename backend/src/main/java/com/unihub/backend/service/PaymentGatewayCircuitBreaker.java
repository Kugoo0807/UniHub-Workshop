package com.unihub.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

class PaymentGatewayCircuitBreaker {

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

    PaymentGatewayCircuitBreaker() {
        this(3, Duration.ofSeconds(30), Clock.systemUTC());
    }

    PaymentGatewayCircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be at least 1");
        }
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
