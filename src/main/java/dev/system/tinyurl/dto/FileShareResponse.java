package dev.system.tinyurl.dto;

import java.time.Instant;

public record FileShareResponse(
        String secretKey,
        String filename,
        String contentType,
        Long sizeBytes,
        String status,
        String shareUrl,
        int downloadCount,
        Integer maxDownloads,
        boolean burnAfterRead,
        Instant expiresAt) {}