package dev.system.tinyurl.id;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param workerId  fixed worker id (local dev / tests). When null, a worker id is leased from Redis.
 * @param leaseTtl  how long a Redis lease lives without a heartbeat
 * @param heartbeat how often a worker renews its lease; must be well under leaseTtl
 */
@ConfigurationProperties("tinyurl.id")
public record IdProperties(Integer workerId, Duration leaseTtl, Duration heartbeat) {

    public IdProperties {
        if (leaseTtl == null) leaseTtl = Duration.ofSeconds(60);
        if (heartbeat == null) heartbeat = Duration.ofSeconds(20);
        if (heartbeat.compareTo(leaseTtl.dividedBy(2)) > 0) {
            throw new IllegalArgumentException("heartbeat must be <= leaseTtl/2");
        }
    }
}