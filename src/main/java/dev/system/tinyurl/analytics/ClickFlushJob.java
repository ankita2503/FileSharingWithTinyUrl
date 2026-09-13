package dev.system.tinyurl.analytics;


import dev.system.tinyurl.Repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Moves buffered click counts from Redis into Postgres. A short Redis lease ensures
 * only one instance flushes at a time; GETDEL makes each read-and-reset atomic so
 * clicks arriving mid-flush land in the next cycle rather than being lost.
 */
@Component
public class ClickFlushJob {

    private static final Logger log = LoggerFactory.getLogger(ClickFlushJob.class);
    static final String LOCK_KEY = "clicks:flush:lock";
    static final int BATCH = 500;

    private final StringRedisTemplate redis;
    private final UrlMappingRepository repo;

    public ClickFlushJob(StringRedisTemplate redis, UrlMappingRepository repo) {
        this.redis = redis;
        this.repo = repo;
    }

    @Scheduled(fixedDelayString = "${tinyurl.analytics.flush-interval:10s}")
    public void flush() {
        Boolean locked = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) return;
        try {
            int flushed = flushOnce();
            if (flushed > 0) log.info("Flushed click counts for {} keys", flushed);
        } catch (RuntimeException e) {
            log.warn("Click flush failed: {}", e.toString());
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    @Transactional
    public int flushOnce() {
        List<String> keys = redis.opsForSet().pop(ClickCounter.DIRTY_SET, BATCH);
        if (keys == null || keys.isEmpty()) return 0;
        int updated = 0;
        for (String key : keys) {
            String v = redis.opsForValue().getAndDelete(ClickCounter.COUNTER_PREFIX + key);
            long delta = v == null ? 0 : Long.parseLong(v);
            if (delta > 0) {
                repo.incrementClickCount(key, delta);
                updated++;
            }
        }
        return updated;
    }
}