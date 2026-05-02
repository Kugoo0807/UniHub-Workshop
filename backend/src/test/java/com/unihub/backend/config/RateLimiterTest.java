package com.unihub.backend.config;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Collections;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class RateLimiterTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long TEST_USER_ID = 9999L;
    private static final String REDIS_KEY = "rate_limit:registration:" + TEST_USER_ID;

    @BeforeEach
    void setup() {
        // Build MockMvc manually without attaching Spring Security filters
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        // Clear the Redis key before each test
        redisTemplate.delete(REDIS_KEY);

        // Simulate one authenticated user
        Long principal = TEST_USER_ID;
        
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void teardown() {
        redisTemplate.delete(REDIS_KEY);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testRateLimiterBlocksAfter10Requests() throws Exception {
        // Send 10 requests first, all should pass through the interceptor
        // API body is missing so it will throw 400 Bad Request, but the interceptor should NOT block (i.e. not 429)
        for (int i = 0; i < 10; i++) {
            final int attempt = i;
            mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/registrations/free"))
                    .andExpect(result -> {
                        int statusCode = result.getResponse().getStatus();
                        assert statusCode != 429 : "Request #" + (attempt + 1) + " was incorrectly blocked";
                    });
        }

        // The 11th request within the same 5-second window => MUST BE BLOCKED (HTTP 429)
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/registrations/free"))
                .andExpect(status().isTooManyRequests());
    }
}
