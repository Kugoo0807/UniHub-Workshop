package com.unihub.backend.config;

import com.unihub.backend.exception.RateLimitExceededException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimiterInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    // Fixed Window logic in Lua to prevent race conditions on EXPIRE
    private static final String RATE_LIMIT_SCRIPT = "local c = redis.call('INCR', KEYS[1]) " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return c";

    private static final int MAX_REQUESTS = 10;
    private static final int WINDOW_SECONDS = 5;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // Apply rate limit only to POST requests
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            // Bypass unauthenticated users; let Security filter handle 401 Unauthorized
            return true;
        }

        String redisKey = "rate_limit:registration:" + userId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
        Long currentCount = redisTemplate.execute(script, Collections.singletonList(redisKey),
                String.valueOf(WINDOW_SECONDS));

        if (currentCount != null && currentCount > MAX_REQUESTS) {
            log.warn("Rate limit exceeded for user {}", userId);
            throw new RateLimitExceededException(
                    "You are acting too quickly. Please wait " + WINDOW_SECONDS + " seconds before trying again.");
        }

        return true;
    }
}
