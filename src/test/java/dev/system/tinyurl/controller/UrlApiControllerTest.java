package dev.system.tinyurl.controller;

import dev.system.tinyurl.Exceptions.*;
import dev.system.tinyurl.analytics.ClickCounter;
import dev.system.tinyurl.api.AppProperties;
import dev.system.tinyurl.ratelimiter.RateLimitFilter;
import dev.system.tinyurl.service.UrlShortenerService;
import dev.system.tinyurl.url.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
 // Boot 4 alt: org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {UrlApiController.class, RedirectController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@Import(ApiExceptionHandler.class)
@EnableConfigurationProperties(AppProperties.class)
class UrlApiControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    UrlShortenerService service;

    @MockitoBean
    ClickCounter clickCounter;

    @TestConfiguration
    static class Meters {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static final String LONG = "https://example.com/page";

    private static UrlShortenerService.ShortenResult result(boolean created) {
        return new UrlShortenerService.ShortenResult("abc1234", LONG, null, created);
    }

    @Test
    void createReturns201WithLocationAndShortUrl() throws Exception {
        when(service.shorten(eq(LONG), isNull(), isNull())).thenReturn(result(true));

        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"" + LONG + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/abc1234"))
                .andExpect(jsonPath("$.shortKey").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc1234"))
                .andExpect(jsonPath("$.longUrl").value(LONG));
    }

    @Test
    void idempotentHitReturns200() throws Exception {
        when(service.shorten(eq(LONG), isNull(), isNull())).thenReturn(result(false));

        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"" + LONG + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void blankUrlIs400ValidationFailed() throws Exception {
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void shortAliasFailsPattern() throws Exception {
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"" + LONG + "\",\"customAlias\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("customAlias")));
    }

    @Test
    void aliasTakenIs409() throws Exception {
        when(service.shorten(eq(LONG), eq("promo"), isNull()))
                .thenThrow(new AliasTakenException("promo"));

        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"" + LONG + "\",\"customAlias\":\"promo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALIAS_TAKEN"));
    }

    @Test
    void invalidUrlIs400() throws Exception {
        when(service.shorten(eq("ftp://x"), isNull(), isNull()))
                .thenThrow(new InvalidUrlException("only http/https allowed"));

        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"ftp://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_URL"));
    }

    @Test
    void redirectReturns302WithNoStore() throws Exception {
        when(service.resolve("abc1234")).thenReturn(LONG);

        mvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", LONG))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void unknownKeyIs404ProblemJson() throws Exception {
        when(service.resolve("zzzzzzz")).thenThrow(new NotFoundException("zzzzzzz"));

        mvc.perform(get("/zzzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void expiredKeyIs410() throws Exception {
        when(service.resolve("old1")).thenThrow(new LinkExpiredException("old1"));

        mvc.perform(get("/old1"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EXPIRED"));
    }

    @Test
    void metadataReturnsJson() throws Exception {
        var m = new UrlMapping(1L, "abc1234", LONG, Instant.parse("2026-09-12T10:00:00Z"), null);
        when(service.metadata("abc1234")).thenReturn(m);

        mvc.perform(get("/api/v1/urls/abc1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortKey").value("abc1234"))
                .andExpect(jsonPath("$.clickCount").value(0));
    }

    @Test
    void overlongPathIsNotTreatedAsRedirectKey() throws Exception {
        mvc.perform(get("/this-path-is-far-too-long"))
                .andExpect(status().isNotFound());
        verify(service, never()).resolve(any());
    }
}