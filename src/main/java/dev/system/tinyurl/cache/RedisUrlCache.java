package dev.system.tinyurl.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside store in Redis. Positive and negative entries share the key
 * {@code url:{shortKey}} so a single GET answers "known?", "missing?" and "what is it?".
 */
@Component
public class RedisUrlCache implements UrlCache {

    private static final Logger log = LoggerFactory.getLogger(RedisUrlCache.class);
    public static final String KEY_PREFIX = "url:";
    public static final String NEGATIVE_SENTINEL = "\u0000MISS";

    private final StringRedisTemplate redis;
    private final Counter hits, misses, negatives, errors;

    public RedisUrlCache(StringRedisTemplate redis, MeterRegistry registry) {
        this.redis = redis;
        this.hits      = registry.counter("tinyurl.cache", "result", "hit");
        this.misses    = registry.counter("tinyurl.cache", "result", "miss");
        this.negatives = registry.counter("tinyurl.cache", "result", "negative");
        this.errors    = registry.counter("tinyurl.cache", "result", "error");
    }

    @Override
    public Optional<CachedUrl> get(String shortKey) {
        try {
            String value = redis.opsForValue().get(key(shortKey));
            if (value == null) { misses.increment(); return Optional.empty(); }
            if (NEGATIVE_SENTINEL.equals(value)) { negatives.increment(); return Optional.of(CachedUrl.miss()); }
            hits.increment();
            return Optional.of(CachedUrl.hit(value));
        } catch (RuntimeException e) {
            errors.increment();
            log.warn("Cache read failed for {}, falling back to DB: {}", shortKey, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(String shortKey, String longUrl, Duration ttl) {
        safely(() -> redis.opsForValue().set(key(shortKey), longUrl, ttl));
    }

    @Override
    public void putNegative(String shortKey, Duration ttl) {
        safely(() -> redis.opsForValue().set(key(shortKey), NEGATIVE_SENTINEL, ttl));
    }

    @Override
    public void evict(String shortKey) {
        safely(() -> redis.delete(key(shortKey)));
    }

    private void safely(Runnable op) {
        try { op.run(); }
        catch (RuntimeException e) { errors.increment(); log.warn("Cache write failed: {}", e.toString()); }
    }

    private static String key(String shortKey) { return KEY_PREFIX + shortKey; }
}
