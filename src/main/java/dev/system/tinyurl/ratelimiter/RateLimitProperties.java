package dev.system.tinyurl.ratelimiter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tinyurl.ratelimit")
public record RateLimitProperties(boolean enabled, int capacity, int refillPerSecond) {
    public RateLimitProperties {
        if (capacity <= 0) capacity = 60;
        if (refillPerSecond <= 0) refillPerSecond = 1;
    }
}
