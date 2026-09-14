package dev.system.tinyurl.dto;

import java.time.Instant;

public record InitiateUploadResponse(
        String secretKey,
        String uploadUrl,
        String uploadContentType,   // client MUST send this exact Content-Type
        String shareUrl,
        Instant expiresAt) {}