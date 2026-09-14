package dev.system.tinyurl.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param endpoint        S3-compatible endpoint (MinIO/R2). Null means real AWS S3.
 * @param uploadUrlTtl    generous: large files take time to transfer
 * @param downloadUrlTtl  deliberately short: the URL is the capability
 */
@ConfigurationProperties("tinyurl.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        long maxFileSizeBytes) {

    public StorageProperties {
        if (region == null || region.isBlank()) region = "us-east-1";
        if (bucket == null || bucket.isBlank()) bucket = "tinyurl-files";
        if (uploadUrlTtl == null) uploadUrlTtl = Duration.ofMinutes(15);
        if (downloadUrlTtl == null) downloadUrlTtl = Duration.ofSeconds(60);
        if (maxFileSizeBytes <= 0) maxFileSizeBytes = 100L * 1024 * 1024;
    }
}