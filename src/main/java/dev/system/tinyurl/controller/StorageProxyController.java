package dev.system.tinyurl.controller;

import dev.system.tinyurl.storage.ProxyFileStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/storage")
@ConditionalOnBean(ProxyFileStorage.class)
public class StorageProxyController {

    private final ProxyFileStorage storage;

    public StorageProxyController(ProxyFileStorage storage) { this.storage = storage; }

    @PutMapping("/u/{token}")
    public ResponseEntity<Void> upload(@PathVariable String token,
                                       HttpServletRequest request) throws IOException {
        var data = storage.resolve(token, true);
        if (data.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        long length = request.getContentLengthLong();
        if (length <= 0) return ResponseEntity.status(HttpStatus.LENGTH_REQUIRED).build();

        try (InputStream in = request.getInputStream()) {
            storage.store(data.get(), in, length);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/d/{token}")
    public void download(@PathVariable String token, HttpServletResponse response) throws IOException {
        var data = storage.resolve(token, false);
        if (data.isEmpty()) { response.setStatus(HttpStatus.FORBIDDEN.value()); return; }

        try (var object = storage.open(data.get())) {
            response.setStatus(HttpStatus.OK.value());
            response.setHeader("Content-Type",
                    object.response().contentType() != null
                            ? object.response().contentType() : "application/octet-stream");
            response.setHeader("Content-Length", String.valueOf(object.response().contentLength()));
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + sanitize(data.get().filename()) + "\"");
            object.transferTo(response.getOutputStream());   // streams; never buffers the whole file
        }
    }

    private static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "download";
        return filename.replaceAll("[\\r\\n\"\\\\/]", "_");
    }
}