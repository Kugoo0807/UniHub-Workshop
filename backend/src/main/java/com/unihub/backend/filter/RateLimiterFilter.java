package com.unihub.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.backend.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimiterFilter extends OncePerRequestFilter {

    private static final String RATELIMIT_PREFIX = "ratelimit:";
    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_WINDOW_SECONDS = 10;
    private static final int REGISTRATION_LIMIT = 5;
    private static final int REGISTRATION_WINDOW_SECONDS = 10;

    /**
     * Endpoint patterns mapped to (limit, window_seconds).
     * Registration endpoints get stricter limits.
     */
    private static final Map<String, int[]> ENDPOINT_RULES = Map.of(
            "/api/v1/registrations", new int[]{REGISTRATION_LIMIT, REGISTRATION_WINDOW_SECONDS}
    );

        private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window_start = tonumber(ARGV[2])
            local member = ARGV[3]
            local limit = tonumber(ARGV[4])

            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)
            local count = redis.call('ZCARD', key)

            if count >= limit then
            return 0
            end

            redis.call('ZADD', key, now, member)
            redis.call('EXPIRE', key, ARGV[5])
            return 1
            """;
        private static final RedisScript<Long> LUA_REDIS_SCRIPT =
            new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        int[] rule = resolveRule(path);
        int limit = rule[0];
        int windowSeconds = rule[1];

        String identifier = resolveIdentifier(request);
        String rateKey = RATELIMIT_PREFIX + identifier;
        long nowMs = System.currentTimeMillis();
        long windowStartMs = nowMs - (windowSeconds * 1000L);

        // Sliding window: remove entries older than window, count current
        String rateKeyMember = nowMs + "-" + UUID.randomUUID().toString().substring(0, 6);

        Long allowed = redis.execute(
            LUA_REDIS_SCRIPT,
            List.of(rateKey),
            String.valueOf(nowMs),
            String.valueOf(windowStartMs),
            rateKeyMember,
            String.valueOf(limit),
            String.valueOf(windowSeconds + 5)
        );

        if (allowed != null && allowed == 0L) {
            log.warn("Rate limit exceeded for identifier={}, path={}", identifier, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(429)
                    .error("Too Many Requests")
                    .message("Rate limit exceeded. Please try again later.")
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int[] resolveRule(String path) {
        for (Map.Entry<String, int[]> entry : ENDPOINT_RULES.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return new int[]{DEFAULT_LIMIT, DEFAULT_WINDOW_SECONDS};
    }

    private String resolveIdentifier(HttpServletRequest request) {
        // Prefer userId from JWT auth context
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return "user:" + userId;
        }
        // Fallback to IP
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return "ip:" + (ip != null ? ip : "unknown");
    }
}
