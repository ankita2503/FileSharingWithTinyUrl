package dev.system.tinyurl.id;

import dev.system.tinyurl.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static dev.system.tinyurl.id.RedisLeaseWorkerIdAssigner.KEY_PREFIX;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RedisLeaseWorkerIdAssignerIT {

    @Autowired StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        var keys = redis.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    @Test
    void twoInstancesGetDistinctSlots() {
        var a = new RedisLeaseWorkerIdAssigner(redis, Duration.ofSeconds(30));
        var b = new RedisLeaseWorkerIdAssigner(redis, Duration.ofSeconds(30));
        assertEquals(0, a.assign());
        assertEquals(1, b.assign());
        assertTrue(a.isHealthy() && b.isHealthy());
    }

    @Test
    void losingTheLeaseMarksInstanceUnhealthy() {
        var a = new RedisLeaseWorkerIdAssigner(redis, Duration.ofSeconds(30));
        int slot = a.assign();
        redis.delete(KEY_PREFIX + slot);         // simulate expiry / takeover
        a.heartbeat();
        assertFalse(a.isHealthy());
    }

    @Test
    void heartbeatDoesNotRenewSomeoneElsesLease() {
        var a = new RedisLeaseWorkerIdAssigner(redis, Duration.ofSeconds(30));
        int slot = a.assign();
        redis.opsForValue().set(KEY_PREFIX + slot, "other-instance", Duration.ofSeconds(5));
        a.heartbeat();
        assertFalse(a.isHealthy());
        assertEquals("other-instance", redis.opsForValue().get(KEY_PREFIX + slot));
    }

    @Test
    void destroyReleasesOnlyOwnLease() {
        var a = new RedisLeaseWorkerIdAssigner(redis, Duration.ofSeconds(30));
        int slot = a.assign();
        a.destroy();
        assertNull(redis.opsForValue().get(KEY_PREFIX + slot));
    }
}