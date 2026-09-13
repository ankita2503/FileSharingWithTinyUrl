package dev.system.tinyurl.controller;


import dev.system.tinyurl.api.AppProperties;
import dev.system.tinyurl.api.ShortenRequest;
import dev.system.tinyurl.api.ShortenResponse;
import dev.system.tinyurl.api.UrlMetadataResponse;
import dev.system.tinyurl.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlApiController {

    private final UrlShortenerService service;
    private final AppProperties app;

    public UrlApiController(UrlShortenerService service, AppProperties app) {
        this.service = service;
        this.app = app;
    }

    @PostMapping
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest req) {
        var result = service.shorten(req.longUrl(), req.customAlias(), req.expiresAt());
        var body = new ShortenResponse(result.shortKey(), app.baseUrl() + "/" + result.shortKey(),
                result.longUrl(), result.expiresAt());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create(body.shortUrl()))
                .body(body);
    }

    @GetMapping("/{key}")
    public UrlMetadataResponse metadata(@PathVariable String key) {
        var m = service.metadata(key);
        return new UrlMetadataResponse(m.getShortKey(), m.getLongUrl(), m.getCreatedAt(),
                m.getExpiresAt(), m.getClickCount());
    }
}