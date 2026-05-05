package com.unihub.backend.service;

public interface SeatLockingService {

    /**
     * Atomically reserve a seat for the given workshop and user.
     * Returns true if reservation succeeds, false if no slots available.
     */
    boolean reserveSeat(String workshopId, String userId);

    /**
     * Release a previously reserved seat for the given workshop and user.
     * Idempotent: if the hold key no longer exists, this is a no-op.
     */
    void releaseSeat(String workshopId, String userId);

    /**
     * Read-only check of remaining slots (no side effects).
     */
    int getRemainingSlots(String workshopId);

    /**
     * Initialize slot count in Redis for a workshop.
     */
    void initSlots(String workshopId, int totalSlots, long ttlSeconds);

    /**
     * Update slot count when total_slots changes.
     * Adjusts the Redis counter by the delta.
     */
    void updateSlots(String workshopId, int newTotalSlots, int oldTotalSlots);

    /**
     * Remove slot key from Redis (e.g., when workshop is completed/cancelled).
     */
    void removeSlots(String workshopId);
}
