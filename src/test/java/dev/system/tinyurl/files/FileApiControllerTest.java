package dev.system.tinyurl.files;

import dev.system.tinyurl.Exceptions.ApiExceptionHandler;
import dev.system.tinyurl.Exceptions.ApiExceptionHandler.FileTooLargeException;
import dev.system.tinyurl.api.AppProperties;
import dev.system.tinyurl.controller.FileApiController;
import dev.system.tinyurl.files.FileShare;
import dev.system.tinyurl.service.FileShareService;
import dev.system.tinyurl.ratelimiter.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FileApiController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@Import(ApiExceptionHandler.class)
@EnableConfigurationProperties(AppProperties.class)
class FileApiControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FileShareService service;

    private static FileShare sampleShare() {
        return new FileShare(1L, "AAAAAAAAAAAAAAAAAAAAAA", "uploads/abc", "report.pdf",
                Instant.parse("2026-09-13T10:00:00Z"), Instant.parse("2026-09-14T10:00:00Z"),
                null, false);
    }

    @Test
    void initiateReturns201WithUploadAndShareUrls() throws Exception {
        when(service.initiate(any(), any(), anyLong(), any(), any(), anyBoolean()))
                .thenReturn(new FileShareService.InitiateResult(
                        sampleShare(), URI.create("http://minio/put"), "application/pdf"));

        mvc.perform(post("/api/v1/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filename":"report.pdf","contentType":"application/pdf","sizeBytes":1024}
                                """))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secretKey").value("AAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(jsonPath("$.uploadUrl").value("http://minio/put"))
                .andExpect(jsonPath("$.shareUrl").value("http://localhost:8080/f/AAAAAAAAAAAAAAAAAAAAAA"));
    }

    @Test
    void blankFilenameIs400() throws Exception {
        mvc.perform(post("/api/v1/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"\",\"sizeBytes\":10}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void oversizedIs413() throws Exception {
        when(service.initiate(any(), any(), anyLong(), any(), any(), anyBoolean()))
                .thenThrow(new FileTooLargeException(999_999_999L, 104_857_600L));

        mvc.perform(post("/api/v1/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"huge.bin\",\"sizeBytes\":999999999}"))
                .andDo(print())
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }
}