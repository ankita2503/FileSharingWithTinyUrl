package dev.system.tinyurl.files;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param defaultTtl     used when the caller doesn't specify one
 * @param maxTtl         hard ceiling; a share can never outlive this
 * @param abandonedAfter how long a PENDING upload waits before being swept
 * @param purgeGrace     how long a DELETED row is kept before the row and bytes go.
 *                       Must exceed the presigned download TTL so an in-flight
 *                       burn-after-read download isn't cut off underneath itself.
 */
@ConfigurationProperties("tinyurl.files")
public record FileShareProperties(Duration defaultTtl,
                                  Duration maxTtl,
                                  Duration abandonedAfter,
                                  Duration purgeGrace) {

    public FileShareProperties {
        if (defaultTtl == null) defaultTtl = Duration.ofHours(24);
        if (maxTtl == null) maxTtl = Duration.ofDays(7);
        if (abandonedAfter == null) abandonedAfter = Duration.ofHours(1);
        if (purgeGrace == null) purgeGrace = Duration.ofHours(1);
        if (defaultTtl.compareTo(maxTtl) > 0) {
            throw new IllegalArgumentException("defaultTtl must not exceed maxTtl");
        }
    }
}