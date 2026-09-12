package dev.system.tinyurl.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param ttl         how long a resolved mapping stays in Redis
 * @param negativeTtl how long an unknown key is remembered as missing
 */
@ConfigurationProperties("tinyurl.cache")
public record CacheProperties(Duration ttl, Duration negativeTtl) {
    public CacheProperties {
        if (ttl == null) ttl = Duration.ofHours(24);
        if (negativeTtl == null) negativeTtl = Duration.ofMinutes(5);
    }
}