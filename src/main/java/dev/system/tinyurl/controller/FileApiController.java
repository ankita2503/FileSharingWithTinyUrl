package dev.system.tinyurl.controller;

import dev.system.tinyurl.api.AppProperties;
import dev.system.tinyurl.dto.FileShareResponse;
import dev.system.tinyurl.dto.InitiateUploadRequest;
import dev.system.tinyurl.dto.InitiateUploadResponse;
import dev.system.tinyurl.files.FileShare;
import dev.system.tinyurl.service.FileShareService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/files")
public class FileApiController {

    private final FileShareService service;
    private final AppProperties app;

    public FileApiController(FileShareService service, AppProperties app) {
        this.service = service;
        this.app = app;
    }

    @PostMapping
    public ResponseEntity<InitiateUploadResponse> initiate(@Valid @RequestBody InitiateUploadRequest req) {
        var result = service.initiate(
                req.filename(),
                req.contentType(),
                req.sizeBytes(),
                req.ttlSeconds() == null ? null : Duration.ofSeconds(req.ttlSeconds()),
                req.maxDownloads(),
                req.burnAfterRead() != null && req.burnAfterRead());   // <-- changed

        var share = result.share();
        var body = new InitiateUploadResponse(
                share.getSecretKey(),
                result.uploadUrl().toString(),
                result.contentType(),
                shareUrl(share),
                share.getExpiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{key}/complete")
    public FileShareResponse complete(@PathVariable("key") String key) {
        return toResponse(service.complete(key));
    }

    @GetMapping("/{key}")
    public FileShareResponse metadata(@PathVariable("key") String key) {
        return toResponse(service.metadata(key));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> revoke(@PathVariable("key") String key) {
        service.revoke(key);
        return ResponseEntity.noContent().build();
    }

    private FileShareResponse toResponse(FileShare s) {
        return new FileShareResponse(
                s.getSecretKey(), s.getFilename(), s.getContentType(), s.getSizeBytes(),
                s.getStatus().name(), shareUrl(s), s.getDownloadCount(), s.getMaxDownloads(),
                s.isBurnAfterRead(), s.getExpiresAt());
    }

    private String shareUrl(FileShare s) {
        return app.baseUrl() + "/f/" + s.getSecretKey();
    }
}