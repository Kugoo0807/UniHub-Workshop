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
        // Tự khởi tạo MockMvc không đính kèm Spring Security filters
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        // Clear redis key trước mỗi lần test
        redisTemplate.delete(REDIS_KEY);

        // Giả lập 1 user đã đăng nhập
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
    void testRateLimiterBlocksAfter5Requests() throws Exception {
        // Gửi 5 requests đầu tiên, phải thành công (hoặc ít nhất là qua được interceptor)
        // Vì API body bị thiếu nên nó sẽ ném lỗi 400 Bad Request, nhưng interceptor thì KHÔNG block (tức là không phải lỗi 429)
        for (int i = 0; i < 5; i++) {
            final int attempt = i;
            mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/registrations/free"))
                    .andExpect(result -> {
                        // Miễn không phải là 429 Too Many Requests là được
                        int statusCode = result.getResponse().getStatus();
                        assert statusCode != 429 : "Request thứ " + (attempt + 1) + " bị block sai";
                    });
        }

        // Request thứ 6 TRONG CÙNG 5 giây => PHẢI BỊ BLOCK (HTTP 429)
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/registrations/free"))
                .andExpect(status().isTooManyRequests());
    }
}
