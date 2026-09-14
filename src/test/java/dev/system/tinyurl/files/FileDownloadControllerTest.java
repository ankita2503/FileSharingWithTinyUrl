package dev.system.tinyurl.controller;

import dev.system.tinyurl.Exceptions.ApiExceptionHandler.FileShareNotFoundException;
import dev.system.tinyurl.Exceptions.ShareGoneException;
import dev.system.tinyurl.files.FileShare;
import dev.system.tinyurl.ratelimiter.RateLimitFilter;
import dev.system.tinyurl.service.FileShareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FileDownloadController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
class FileDownloadControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAA";

    @Autowired MockMvc mvc;
    @MockitoBean FileShareService service;

    @TestConfiguration
    static class Clocks {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    private static FileShare readyShare() {
        var share = new FileShare(1L, KEY, "uploads/abc", "quarterly report.pdf",
                NOW.minusSeconds(60), NOW.plusSeconds(3600), null, false);
        share.markReady(2048, "application/pdf");
        return share;
    }

    @Test
    void landingPageShowsFilenameAndDownloadLink() throws Exception {
        when(service.metadata(KEY)).thenReturn(readyShare());

        mvc.perform(get("/f/" + KEY))
                .andExpect(status().isOk())
                .andExpect(view().name("file-landing"))
                .andExpect(model().attribute("filename", "quarterly report.pdf"))
                .andExpect(model().attribute("size", "2.0 KB"))
                .andExpect(model().attribute("downloadUrl", "/f/" + KEY + "/download"))
                .andExpect(model().attribute("expiresIn", "1 hour"));
    }

    @Test
    void landingPageForUnknownKeyShowsUnavailable() throws Exception {
        when(service.metadata(KEY)).thenThrow(new FileShareNotFoundException(KEY));

        mvc.perform(get("/f/" + KEY))
                .andExpect(view().name("file-unavailable"));
    }

    @Test
    void downloadRedirectsToPresignedUrl() throws Exception {
        when(service.issueDownload(KEY)).thenReturn(URI.create("http://minio/signed"));

        mvc.perform(get("/f/" + KEY + "/download"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://minio/signed"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void downloadOfGoneShareIs410() throws Exception {
        when(service.issueDownload(KEY)).thenThrow(new ShareGoneException(KEY));

        mvc.perform(get("/f/" + KEY + "/download"))
                .andExpect(status().isGone());
    }

    @Test
    void downloadOfUnknownShareIs404() throws Exception {
        when(service.issueDownload(KEY)).thenThrow(new FileShareNotFoundException(KEY));

        mvc.perform(get("/f/" + KEY + "/download"))
                .andExpect(status().isNotFound());
    }
}