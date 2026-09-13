package dev.system.tinyurl.api;

import java.time.Instant;

public record ShortenResponse(String shortKey, String shortUrl, String longUrl, Instant expiresAt) {
}
