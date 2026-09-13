package dev.system.tinyurl.analytics;

import dev.system.tinyurl.Repository.UrlMappingRepository;
import dev.system.tinyurl.TestcontainersConfiguration;
import dev.system.tinyurl.url.UrlMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "tinyurl.analytics.flush-interval=1h") // keep the scheduler out of the way
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ClickFlushJobIT {

    @Autowired
    ClickCounter counter;
    @Autowired
    ClickFlushJob job;
    @Autowired
    UrlMappingRepository repo;
    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        repo.deleteAll();
        var conn = redis.getConnectionFactory().getConnection();
        conn.serverCommands().flushDb();
        conn.close();
    }

    @Test
    void flushMovesBufferedClicksToPostgres() {
        repo.saveAndFlush(new UrlMapping(1L, "clk00001", "https://example.com/1", Instant.now(), null));
        repo.saveAndFlush(new UrlMapping(2L, "clk00002", "https://example.com/2", Instant.now(), null));

        counter.record("clk00001");
        counter.record("clk00001");
        counter.record("clk00001");
        counter.record("clk00002");

        assertEquals(2, job.flushOnce());

        assertEquals(3, repo.findByShortKey("clk00001").orElseThrow().getClickCount());
        assertEquals(1, repo.findByShortKey("clk00002").orElseThrow().getClickCount());
        assertNull(redis.opsForValue().get(ClickCounter.COUNTER_PREFIX + "clk00001"));
        assertEquals(0, redis.opsForSet().size(ClickCounter.DIRTY_SET));
    }

    @Test
    void secondFlushIsNoOpAndCountsAccumulate() {
        repo.saveAndFlush(new UrlMapping(3L, "clk00003", "https://example.com/3", Instant.now(), null));

        counter.record("clk00003");
        job.flushOnce();
        assertEquals(0, job.flushOnce());

        counter.record("clk00003");
        counter.record("clk00003");
        job.flushOnce();
        assertEquals(3, repo.findByShortKey("clk00003").orElseThrow().getClickCount());
    }

    @Test
    void flushSkipsWhenLockHeldByAnotherInstance() {
        repo.saveAndFlush(new UrlMapping(4L, "clk00004", "https://example.com/4", Instant.now(), null));
        counter.record("clk00004");
        redis.opsForValue().set(ClickFlushJob.LOCK_KEY, "other");

        job.flush();   // the scheduled entry point, not flushOnce

        assertEquals(0, repo.findByShortKey("clk00004").orElseThrow().getClickCount());
        assertEquals("1", redis.opsForValue().get(ClickCounter.COUNTER_PREFIX + "clk00004"));
        redis.delete(ClickFlushJob.LOCK_KEY);
    }
}
