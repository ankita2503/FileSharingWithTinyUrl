package dev.system.tinyurl.cache;

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

    public static final String KEY_PREFIX = "url:";
    /** Sentinel for negative entries; NUL prefix can never collide with a real URL. */
    public static final String NEGATIVE_SENTINEL = "\u0000MISS";

    private final StringRedisTemplate redis;

    public RedisUrlCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<CachedUrl> get(String shortKey) {
        String value = redis.opsForValue().get(key(shortKey));
        if (value == null) return Optional.empty();
        return Optional.of(NEGATIVE_SENTINEL.equals(value) ? CachedUrl.miss() : CachedUrl.hit(value));
    }

    @Override
    public void put(String shortKey, String longUrl, Duration ttl) {
        redis.opsForValue().set(key(shortKey), longUrl, ttl);
    }

    @Override
    public void putNegative(String shortKey, Duration ttl) {
        redis.opsForValue().set(key(shortKey), NEGATIVE_SENTINEL, ttl);
    }

    @Override
    public void evict(String shortKey) {
        redis.delete(key(shortKey));
    }

    private static String key(String shortKey) {
        return KEY_PREFIX + shortKey;
    }
}
