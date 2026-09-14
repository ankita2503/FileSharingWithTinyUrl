package dev.system.tinyurl.controller;

import dev.system.tinyurl.Exceptions.ApiExceptionHandler;
import dev.system.tinyurl.Exceptions.ShareGoneException;
import dev.system.tinyurl.files.FileShare;
import dev.system.tinyurl.service.FileShareService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Recipient-facing pages. Secret keys are 22 characters, so these paths can never
 * collide with the 1-8 character short-link route at "/{key}".
 *
 * This is a @Controller, not a @RestController: a human clicking a dead link should
 * get an HTML page, not RFC 9457 problem JSON. The exceptions are therefore caught
 * here rather than being left to ApiExceptionHandler, which still serves /api/v1/**.
 */
@Controller
public class FileDownloadController {

    private final FileShareService service;
    private final Clock clock;

    public FileDownloadController(FileShareService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/f/{key}")
    public String landing(@PathVariable("key") String key, Model model) {
        FileShare share;
        try {
            share = service.metadata(key);
        } catch (ApiExceptionHandler.FileShareNotFoundException e) {
            return notAvailable(model, "This link doesn't exist.", HttpStatus.NOT_FOUND);
        }

        Instant now = clock.instant();
        if (share.getStatus() == FileShare.Status.PENDING) {
            return notAvailable(model, "This file hasn't finished uploading yet.", HttpStatus.CONFLICT);
        }
        if (!share.isDownloadable(now)) {
            return notAvailable(model, "This file is no longer available. "
                            + "Shared files are deleted once they expire or reach their download limit.",
                    HttpStatus.GONE);
        }

        model.addAttribute("filename", share.getFilename());
        model.addAttribute("size", humanSize(share.getSizeBytes()));
        model.addAttribute("downloadUrl", "/f/" + share.getSecretKey() + "/download");
        model.addAttribute("expiresIn", humanDuration(Duration.between(now, share.getExpiresAt())));
        model.addAttribute("remaining", share.remainingDownloads());
        model.addAttribute("burnAfterRead", share.isBurnAfterRead());
        return "file-landing";
    }

    @GetMapping("/f/{key}/download")
    public ResponseEntity<Void> download(@PathVariable("key") String key) {
        try {
            var url = service.issueDownload(key);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, url.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        } catch (ApiExceptionHandler.FileShareNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (ShareGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        } catch (ApiExceptionHandler.UploadNotCompletedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    private String notAvailable(Model model, String message, HttpStatus status) {
        model.addAttribute("message", message);
        model.addAttribute("status", status.value());
        return "file-unavailable";
    }

    static String humanSize(Long bytes) {
        if (bytes == null) return "unknown size";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    static String humanDuration(Duration d) {
        if (d.isNegative() || d.isZero()) return "less than a minute";
        long days = d.toDays();
        if (days > 0) return days + (days == 1 ? " day" : " days");
        long hours = d.toHours();
        if (hours > 0) return hours + (hours == 1 ? " hour" : " hours");
        long minutes = Math.max(1, d.toMinutes());
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}