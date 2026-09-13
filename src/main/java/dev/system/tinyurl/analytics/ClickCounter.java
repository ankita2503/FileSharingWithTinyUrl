package dev.system.tinyurl.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClickCounter {

    private static final Logger log = LoggerFactory.getLogger(ClickCounter.class);
    static final String COUNTER_PREFIX = "clicks:";
    static final String DIRTY_SET = "clicks:dirty";

    private final StringRedisTemplate redis;

    public ClickCounter(StringRedisTemplate redis) { this.redis = redis; }

    /** Fire-and-forget: a lost click is acceptable, a slow redirect is not. */
    public void record(String shortKey) {
        try {
            redis.opsForValue().increment(COUNTER_PREFIX + shortKey);
            redis.opsForSet().add(DIRTY_SET, shortKey);
        } catch (RuntimeException e) {
            log.debug("Click not recorded for {}: {}", shortKey, e.toString());
        }
    }
}
