package dev.system.tinyurl.files;

import dev.system.tinyurl.Exceptions.ApiExceptionHandler.FileShareNotFoundException;
import dev.system.tinyurl.Exceptions.ApiExceptionHandler.FileTooLargeException;
import dev.system.tinyurl.Exceptions.ApiExceptionHandler.InvalidShareRequestException;
import dev.system.tinyurl.Exceptions.ApiExceptionHandler.UploadNotCompletedException;
import dev.system.tinyurl.Repository.FileShareRepository;
import dev.system.tinyurl.TestcontainersConfiguration;
import dev.system.tinyurl.service.FileShareService;
import dev.system.tinyurl.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FileShareServiceIT {

    @Autowired FileShareService service;
    @Autowired
    FileShareRepository repo;
    @Autowired FileStorage storage;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    /**
     * Uploads to the presigned URL. The Content-Type header must match the value the
     * URL was signed with, otherwise S3/MinIO returns 403 SignatureDoesNotMatch, so it
     * is taken from the InitiateResult rather than hardcoded.
     */
    private void upload(FileShareService.InitiateResult init, byte[] content) throws Exception {
        var res = http.send(HttpRequest.newBuilder(init.uploadUrl())
                        .header("Content-Type", init.contentType())
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(200, res.statusCode(), "presigned upload should succeed");
    }

    @Test
    void initiateCreatesPendingShare() {
        var result = service.initiate("report.pdf", "application/pdf", 1024, null, null, false);

        assertEquals(22, result.share().getSecretKey().length());
        assertEquals(FileShare.Status.PENDING, result.share().getStatus());
        assertTrue(result.share().getObjectKey().startsWith("uploads/"));
        assertFalse(result.share().getObjectKey().contains(result.share().getSecretKey()),
                "object key must not be derivable from the secret key");
        assertNotNull(result.uploadUrl());
        assertEquals("application/pdf", result.contentType());
    }

    @Test
    void missingContentTypeDefaultsToOctetStream() {
        var result = service.initiate("blob.bin", null, 10, null, null, false);
        assertEquals("application/octet-stream", result.contentType());
    }

    @Test
    void completeMarksReadyWithSizeFromStorage() throws Exception {
        byte[] content = "hello file sharing".getBytes(StandardCharsets.UTF_8);
        var init = service.initiate("notes.txt", "text/plain", 9_999, null, null, false);

        upload(init, content);
        var ready = service.complete(init.share().getSecretKey());

        assertEquals(FileShare.Status.READY, ready.getStatus());
        // real size from storage, not the declared 9_999
        assertEquals(content.length, ready.getSizeBytes());
    }

    @Test
    void completeWithoutUploadIsConflict() {
        var init = service.initiate("ghost.bin", null, 100, null, null, false);
        assertThrows(UploadNotCompletedException.class,
                () -> service.complete(init.share().getSecretKey()));
    }

    @Test
    void completeIsIdempotent() throws Exception {
        var init = service.initiate("a.txt", "text/plain", 10, null, null, false);
        upload(init, "abc".getBytes(StandardCharsets.UTF_8));

        var first = service.complete(init.share().getSecretKey());
        var second = service.complete(init.share().getSecretKey());

        assertEquals(FileShare.Status.READY, second.getStatus());
        assertEquals(first.getSizeBytes(), second.getSizeBytes());
    }

    @Test
    void oversizedDeclarationIsRejectedBeforeUpload() {
        assertThrows(FileTooLargeException.class,
                () -> service.initiate("huge.bin", null, 200L * 1024 * 1024, null, null, false));
    }

    @Test
    void ttlBeyondMaximumIsRejected() {
        assertThrows(InvalidShareRequestException.class,
                () -> service.initiate("a.txt", null, 10, Duration.ofDays(30), null, false));
    }

    @Test
    void blankFilenameIsRejected() {
        assertThrows(InvalidShareRequestException.class,
                () -> service.initiate("   ", null, 10, null, null, false));
    }

    @Test
    void burnAfterReadImpliesOneDownload() {
        var init = service.initiate("secret.txt", null, 10, null, null, true);
        assertEquals(1, init.share().getMaxDownloads());
        assertTrue(init.share().isBurnAfterRead());
    }

    @Test
    void explicitMaxDownloadsIsPreservedWithoutBurn() {
        var init = service.initiate("shared.txt", null, 10, null, 5, false);
        assertEquals(5, init.share().getMaxDownloads());
        assertFalse(init.share().isBurnAfterRead());
    }

    @Test
    void revokeDeletesObjectAndMarksDeleted() throws Exception {
        var init = service.initiate("temp.txt", "text/plain", 10, null, null, false);
        upload(init, "bye".getBytes(StandardCharsets.UTF_8));
        service.complete(init.share().getSecretKey());

        service.revoke(init.share().getSecretKey());

        assertEquals(FileShare.Status.DELETED,
                repo.findBySecretKey(init.share().getSecretKey()).orElseThrow().getStatus());
        assertTrue(storage.head(init.share().getObjectKey()).isEmpty(),
                "object should be gone from storage");
    }

    @Test
    void unknownKeyIsNotFound() {
        assertThrows(FileShareNotFoundException.class, () -> service.metadata("nope"));
    }
}
