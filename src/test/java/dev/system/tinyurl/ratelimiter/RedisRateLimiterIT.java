package dev.system.tinyurl.ratelimiter;

import dev.system.tinyurl.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "tinyurl.ratelimit.enabled=true",
        "tinyurl.ratelimit.capacity=5",
        "tinyurl.ratelimit.refill-per-second=1"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RedisRateLimiterIT {

    @Autowired RedisRateLimiter limiter;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        var keys = redis.keys(RedisRateLimiter.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    @Test
    void allowsUpToCapacityThenDenies() {
        for (int i = 0; i < 5; i++) {
            var d = limiter.check("client-a");
            assertTrue(d.allowed(), "call " + (i + 1) + " should be allowed");
            assertEquals(4 - i, d.remaining());
        }
        var denied = limiter.check("client-a");
        assertFalse(denied.allowed());
        assertEquals(0, denied.remaining());
        assertEquals(1, denied.retryAfterSeconds());
    }

    @Test
    void clientsAreIsolated() {
        for (int i = 0; i < 6; i++) limiter.check("client-b");
        assertFalse(limiter.check("client-b").allowed());
        assertTrue(limiter.check("client-c").allowed());
    }

    @Test
    void bucketKeyHasTtl() {
        limiter.check("client-d");
        Long ttl = redis.getExpire(RedisRateLimiter.KEY_PREFIX + "client-d");
        assertNotNull(ttl);
        assertTrue(ttl > 0);
    }
}
