package dev.system.tinyurl.controller;

import dev.system.tinyurl.analytics.ClickCounter;
import dev.system.tinyurl.service.UrlShortenerService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final UrlShortenerService service;
    private final ClickCounter clickCounter;
    private final Timer redirectTimer;

    public RedirectController(UrlShortenerService service,
                              ClickCounter clickCounter,
                              MeterRegistry registry) {
        this.service = service;
        this.clickCounter = clickCounter;
        this.redirectTimer = Timer.builder("tinyurl.redirect")
                .description("Time to resolve a short key to its target URL")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * 302 rather than 301: browsers cache 301 permanently, which would hide
     * clicks from analytics and make expiry/deletion unobservable.
     */
    @GetMapping("/{key:[A-Za-z0-9_-]{1,8}}")
    public ResponseEntity<Void> redirect(@PathVariable("key") String key) {
        String target = redirectTimer.record(() -> service.resolve(key));
        clickCounter.record(key);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, target)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}