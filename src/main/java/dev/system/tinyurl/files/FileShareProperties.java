package dev.system.tinyurl.files;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param defaultTtl used when the caller doesn't specify
 * @param maxTtl     hard ceiling; a share can never outlive this
 */
@ConfigurationProperties("tinyurl.files")
public record FileShareProperties(Duration defaultTtl, Duration maxTtl, Duration abandonedAfter) {
    public FileShareProperties {
        if (defaultTtl == null) defaultTtl = Duration.ofHours(24);
        if (maxTtl == null) maxTtl = Duration.ofDays(7);
        if (abandonedAfter == null) abandonedAfter = Duration.ofHours(1);
        if (defaultTtl.compareTo(maxTtl) > 0) {
            throw new IllegalArgumentException("defaultTtl must not exceed maxTtl");
        }
    }
}