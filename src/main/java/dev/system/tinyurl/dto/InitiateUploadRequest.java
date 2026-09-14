package dev.system.tinyurl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InitiateUploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @Size(max = 255) String contentType,
        @Positive long sizeBytes,
        @Positive Long ttlSeconds,
        @Positive Integer maxDownloads,
        Boolean burnAfterRead) {

    public InitiateUploadRequest {
        if (burnAfterRead == null) burnAfterRead = false;
    }
}