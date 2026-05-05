package com.unihub.backend.service;

import com.unihub.backend.enums.IdempotencyState;
import com.unihub.backend.dto.IdempotencyResult;

import java.time.Duration;

public interface IdempotencyService {

    /**
     * Get the current state of an idempotency key.
     */
    IdempotencyState getState(String idempotencyKey);

    /**
     * Mark a key as in-flight with a short TTL.
     */
    void markInFlight(String idempotencyKey, Duration ttl);

    /**
     * Store the final result with TTL (typically 24h).
     */
    void storeResult(String idempotencyKey, IdempotencyResult result, Duration ttl);
}
