package dev.system.tinyurl.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ShortenRequest(
        @NotBlank @Size(max = 2048) String longUrl,
        @Pattern(regexp = "^[A-Za-z0-9_-]{4,8}$",
                message = "alias must be 4-8 chars of letters, digits, '_' or '-'")
        String customAlias,
        Instant expiresAt) {}