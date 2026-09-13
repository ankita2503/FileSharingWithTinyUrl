package dev.system.tinyurl.ratelimiter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock RedisRateLimiter limiter;

    @Test
    void allowedRequestPassesThroughWithRemainingHeader() throws Exception {
        when(limiter.check("10.0.0.1")).thenReturn(new RedisRateLimiter.Decision(true, 42, 0));
        var filter = new RateLimitFilter(limiter);
        var req = new MockHttpServletRequest("GET", "/abc1234");
        req.setRemoteAddr("10.0.0.1");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotNull(chain.getRequest(), "chain should have been invoked");
        assertEquals("42", res.getHeader("X-RateLimit-Remaining"));
        assertEquals(200, res.getStatus());
    }

    @Test
    void deniedRequestIs429WithRetryAfter() throws Exception {
        when(limiter.check("10.0.0.1")).thenReturn(new RedisRateLimiter.Decision(false, 0, 7));
        var filter = new RateLimitFilter(limiter);
        var req = new MockHttpServletRequest("GET", "/abc1234");
        req.setRemoteAddr("10.0.0.1");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNull(chain.getRequest(), "chain must not be invoked");
        assertEquals(429, res.getStatus());
        assertEquals("7", res.getHeader("Retry-After"));
        assertTrue(res.getContentAsString().contains("RATE_LIMITED"));
        assertTrue(res.getContentType().startsWith("application/problem+json"));
    }

    @Test
    void forwardedForTakesPrecedenceOverRemoteAddr() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.2");
        assertEquals("203.0.113.9", RateLimitFilter.clientKey(req));
    }

    @Test
    void actuatorIsNotRateLimited() throws Exception {
        var filter = new RateLimitFilter(limiter);
        var req = new MockHttpServletRequest("GET", "/actuator/health");
        req.setRequestURI("/actuator/health");
        var res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        verifyNoInteractions(limiter);
    }
}
