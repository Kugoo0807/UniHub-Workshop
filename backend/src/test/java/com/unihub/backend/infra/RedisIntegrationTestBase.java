package com.unihub.backend.infra;

import com.unihub.backend.config.RedisConfig;
import com.unihub.backend.filter.RateLimiterFilter;
import com.unihub.backend.service.IdempotencyServiceImpl;
import com.unihub.backend.service.SeatLockingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = RedisIntegrationTestBase.RedisTestConfig.class)
public abstract class RedisIntegrationTestBase {

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.timeout", () -> "2000ms");
    }

    @Autowired
    protected StringRedisTemplate redis;

    @BeforeEach
    void flushRedis() {
        if (redis.getConnectionFactory() != null) {
            redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        }
    }

    @TestConfiguration
    @Import({
            RedisConfig.class,
            SeatLockingServiceImpl.class,
            IdempotencyServiceImpl.class,
            RateLimiterFilter.class,
    })
    static class RedisTestConfig {}
}
