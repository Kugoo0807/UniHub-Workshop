package com.unihub.backend.listener;

import com.unihub.backend.service.SeatLockingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Listens for expired hold keys in Redis.
 * When a hold:{workshopId}:{userId} key expires (TTL 10 min),
 * automatically releases the reserved seat back to the workshop pool.
 *
 * Requires Redis Keyspace Notifications to be enabled:
 *   CONFIG SET notify-keyspace-events Ex
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.redis.keyspace-listener.enabled", havingValue = "true", matchIfMissing = true)
public class RedisKeyspaceListener extends KeyExpirationEventMessageListener {

    private static final String HOLD_KEY_PREFIX = "hold:";

    private final SeatLockingService seatLockingService;

    public RedisKeyspaceListener(RedisMessageListenerContainer listenerContainer,
                                  SeatLockingService seatLockingService) {
        super(listenerContainer);
        this.seatLockingService = seatLockingService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody());

        if (expiredKey == null || !expiredKey.startsWith(HOLD_KEY_PREFIX)) {
            return;
        }

        // Parse hold:{workshopId}:{userId}
        String payload = expiredKey.substring(HOLD_KEY_PREFIX.length());
        int separatorIndex = payload.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= payload.length() - 1) {
            log.warn("Invalid hold key format: {}", expiredKey);
            return;
        }

        String workshopId = payload.substring(0, separatorIndex);
        String userId = payload.substring(separatorIndex + 1);

        log.info("Hold key expired for workshop={}, user={}; auto-releasing seat", workshopId, userId);
        seatLockingService.releaseSeat(workshopId, userId);
    }
}
