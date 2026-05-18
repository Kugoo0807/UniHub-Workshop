package com.unihub.backend.service;

import com.unihub.backend.dto.IdempotencyResult;
import com.unihub.backend.enums.IdempotencyState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final String KEY_PREFIX = "payment:";

    private final StringRedisTemplate redis;

    @Override
    public IdempotencyState getState(String idempotencyKey) {
        String key = buildKey(idempotencyKey);
        String value = redis.opsForValue().get(key);

        if (value == null) {
            return IdempotencyState.NOT_FOUND;
        }

        return switch (value) {
            case "IN_FLIGHT" -> IdempotencyState.IN_FLIGHT;
            case "SUCCESS" -> IdempotencyState.SUCCESS;
            case "FAILED" -> IdempotencyState.FAILED;
            default -> {
                log.warn("Unknown idempotency state in Redis: key={}, value={}", key, value);
                yield IdempotencyState.NOT_FOUND;
            }
        };
    }

    @Override
    public boolean tryMarkInFlight(String idempotencyKey, Duration ttl) {
        String key = buildKey(idempotencyKey);
        Boolean success = redis.opsForValue().setIfAbsent(key, "IN_FLIGHT", ttl);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void storeResult(String idempotencyKey, IdempotencyResult result, Duration ttl) {
        String key = buildKey(idempotencyKey);
        String value = result.getStatus(); // SUCCESS or FAILED
        redis.opsForValue().set(key, value, ttl);
        log.debug("Stored idempotency result: key={}, status={}", idempotencyKey, value);
    }

    private String buildKey(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }
}
