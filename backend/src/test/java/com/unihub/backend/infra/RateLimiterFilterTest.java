package com.unihub.backend.infra;

import com.unihub.backend.filter.RateLimiterFilter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterFilterTest extends RedisIntegrationTestBase {

    @Autowired
    private RateLimiterFilter rateLimiterFilter;

    @Test
    void registrationEndpointAllowsFiveAndBlocksSixth() throws ServletException, IOException {
        String path = "/api/v1/registrations";
        String uniqueTestIp = "10.0.0." + UUID.randomUUID().toString().substring(0, 5);

        // Send 5 valid requests
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr(uniqueTestIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            rateLimiterFilter.doFilter(request, response, filterChain);

            // Assert that the request was allowed (status 200) and the filter chain was invoked
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        // 6th request should be blocked
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", path);
        blockedRequest.setRemoteAddr(uniqueTestIp);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedFilterChain = new MockFilterChain();

        rateLimiterFilter.doFilter(blockedRequest, blockedResponse, blockedFilterChain);

        // Assert that the request was blocked (status 429) and the filter chain was NOT invoked
        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedFilterChain.getRequest()).isNull();
        assertThat(blockedResponse.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void registrationEndpointReleasesBlockAfterWindow() throws ServletException, IOException, InterruptedException {
        String path = "/api/v1/registrations";
        String uniqueTestIp = "10.0.0." + UUID.randomUUID().toString().substring(0, 5);

        // Send 5 valid requests
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr(uniqueTestIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            rateLimiterFilter.doFilter(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // 6th request should be blocked
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", path);
        blockedRequest.setRemoteAddr(uniqueTestIp);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedFilterChain = new MockFilterChain();

        rateLimiterFilter.doFilter(blockedRequest, blockedResponse, blockedFilterChain);
        assertThat(blockedResponse.getStatus()).isEqualTo(429);

        // Wait for 10 seconds to ensure the sliding window has moved past the first 5 requests
        Thread.sleep(10100);

        // 7th request should now be allowed
        MockHttpServletRequest recoveredRequest = new MockHttpServletRequest("POST", path);
        recoveredRequest.setRemoteAddr(uniqueTestIp);
        MockHttpServletResponse recoveredResponse = new MockHttpServletResponse();
        MockFilterChain recoveredFilterChain = new MockFilterChain();

        rateLimiterFilter.doFilter(recoveredRequest, recoveredResponse, recoveredFilterChain);

        // Assert that the request was allowed (status 200) and the filter chain was invoked
        assertThat(recoveredResponse.getStatus()).isEqualTo(200);
        assertThat(recoveredFilterChain.getRequest()).isNotNull();
    }

    @Test
    void defaultEndpointAllowsTwentyAndBlocksTwentyFirst() throws ServletException, IOException {
        String path = "/api/v1/auth/me";
        String uniqueTestIp = "10.0.0." + UUID.randomUUID().toString().substring(0, 5);

        // Send 20 valid requests
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRemoteAddr(uniqueTestIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            rateLimiterFilter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        // 21st request should be blocked
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("GET", path);
        blockedRequest.setRemoteAddr(uniqueTestIp);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedFilterChain = new MockFilterChain();

        rateLimiterFilter.doFilter(blockedRequest, blockedResponse, blockedFilterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedFilterChain.getRequest()).isNull();
        assertThat(blockedResponse.getContentAsString()).contains("Too Many Requests");
    }
}