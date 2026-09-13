package dev.system.tinyurl.api;

import java.time.Instant;

public record UrlMetadataResponse(String shortKey, String longUrl, Instant createdAt,
                                  Instant expiresAt, long clickCount) {
}
