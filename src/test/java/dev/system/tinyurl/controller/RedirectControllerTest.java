package dev.system.tinyurl.controller;

import dev.system.tinyurl.Exceptions.ApiExceptionHandler;
import dev.system.tinyurl.Exceptions.NotFoundException;
import dev.system.tinyurl.analytics.ClickCounter;

import dev.system.tinyurl.ratelimiter.RateLimitFilter;
import dev.system.tinyurl.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RedirectController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@Import(ApiExceptionHandler.class)
class RedirectControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    UrlShortenerService service;
    @MockitoBean
    ClickCounter clickCounter;

    @Test
    void successfulRedirectRecordsClick() throws Exception {
        when(service.resolve("abc1234")).thenReturn("https://example.com");

        mvc.perform(get("/abc1234")).andExpect(status().isFound());

        verify(clickCounter).record("abc1234");
    }

    @Test
    void notFoundDoesNotRecordClick() throws Exception {
        when(service.resolve("missing1")).thenThrow(new NotFoundException("missing1"));

        mvc.perform(get("/missing1")).andExpect(status().isNotFound());

        verify(clickCounter, never()).record(any());
    }
}