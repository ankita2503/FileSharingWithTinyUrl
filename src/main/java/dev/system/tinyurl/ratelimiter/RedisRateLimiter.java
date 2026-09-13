package dev.system.tinyurl.ratelimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Token bucket per client key, evaluated atomically in Redis. Fails OPEN: if Redis
 * is unreachable the request is allowed and the failure is logged/metered, because
 * a dead cache must not take the redirect path down with it.
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    static final String KEY_PREFIX = "rl:";

    /** returns {allowed(0|1), remainingTokens, retryAfterSeconds} */
    private static final RedisScript<List> SCRIPT = RedisScript.of("""
            local key      = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill   = tonumber(ARGV[2])   -- tokens per second
            local now      = tonumber(ARGV[3])   -- epoch millis
            local ttl      = tonumber(ARGV[4])   -- seconds

            local data   = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts     = tonumber(data[2])
            if tokens == nil then tokens = capacity; ts = now end

            local elapsed = math.max(0, now - ts) / 1000
            tokens = math.min(capacity, tokens + elapsed * refill)

            local allowed = 0
            local retry = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            else
              retry = math.ceil((1 - tokens) / refill)
            end

            redis.call('HSET', key, 'tokens', tokens, 'ts', now)
            redis.call('EXPIRE', key, ttl)
            return { allowed, math.floor(tokens), retry }
            """, List.class);

    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
        static Decision open() { return new Decision(true, -1, 0); }
    }

    private final StringRedisTemplate redis;
    private final RateLimitProperties props;
    private final Clock clock;

    public RedisRateLimiter(StringRedisTemplate redis, RateLimitProperties props, Clock clock) {
        this.redis = redis;
        this.props = props;
        this.clock = clock;
    }

    public Decision check(String clientKey) {
        if (!props.enabled()) return Decision.open();
        try {
            long ttl = Math.max(1, props.capacity() / props.refillPerSecond()) * 2L;
            List<?> r = redis.execute(SCRIPT, List.of(KEY_PREFIX + clientKey),
                    String.valueOf(props.capacity()),
                    String.valueOf(props.refillPerSecond()),
                    String.valueOf(clock.millis()),
                    String.valueOf(ttl));
            return new Decision(((Number) r.get(0)).longValue() == 1,
                    ((Number) r.get(1)).longValue(),
                    ((Number) r.get(2)).longValue());
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, failing open: {}", e.toString());
            return Decision.open();
        }
    }
}
