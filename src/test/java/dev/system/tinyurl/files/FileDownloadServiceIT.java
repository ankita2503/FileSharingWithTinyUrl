package dev.system.tinyurl.files;

import dev.system.tinyurl.Exceptions.ApiExceptionHandler;
import dev.system.tinyurl.Exceptions.ShareGoneException;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "tinyurl.files.sweep-interval=1h")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FileDownloadServiceIT {

    @Autowired
    FileShareService service;
    @Autowired
    FileShareRepository repo;
    @Autowired
    FileStorage storage;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    /**
     * Creates a READY share with the given content and returns its secret key.
     */
    private String readyShare(String filename, Integer maxDownloads, boolean burn) throws Exception {
        var init = service.initiate(filename, "text/plain", 1024, null, maxDownloads, burn);
        http.send(HttpRequest.newBuilder(init.uploadUrl())
                        .header("Content-Type", init.contentType())
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(
                                "file content".getBytes(StandardCharsets.UTF_8)))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        service.complete(init.share().getSecretKey());
        return init.share().getSecretKey();
    }

    @Test
    void downloadUrlFetchesTheRealBytes() throws Exception {
        String key = readyShare("notes.txt", null, false);

        var url = service.issueDownload(key);
        var res = http.send(HttpRequest.newBuilder(url).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, res.statusCode());
        assertEquals("file content", res.body());
        assertTrue(res.headers().firstValue("content-disposition").orElse("").contains("notes.txt"));
    }

    @Test
    void downloadIncrementsCount() throws Exception {
        String key = readyShare("a.txt", null, false);

        service.issueDownload(key);
        service.issueDownload(key);

        assertEquals(2, repo.findBySecretKey(key).orElseThrow().getDownloadCount());
    }

    @Test
    void burnAfterReadIsSingleUse() throws Exception {
        String key = readyShare("secret.txt", null, true);

        assertNotNull(service.issueDownload(key));
        assertThrows(ShareGoneException.class, () -> service.issueDownload(key));

        var share = repo.findBySecretKey(key).orElseThrow();
        assertEquals(FileShare.Status.DELETED, share.getStatus());
        assertNotNull(share.getDeletedAt());
    }

    @Test
    void burnedObjectSurvivesUntilSweptSoInFlightDownloadsWork() throws Exception {
        String key = readyShare("secret.txt", null, true);
        var url = service.issueDownload(key);

        // the link is dead immediately...
        assertThrows(ShareGoneException.class, () -> service.issueDownload(key));

        // ...but the already-issued URL still resolves; the sweeper removes the bytes later
        var res = http.send(HttpRequest.newBuilder(url).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
    }

    @Test
    void downloadLimitIsEnforced() throws Exception {
        String key = readyShare("limited.txt", 2, false);

        service.issueDownload(key);
        service.issueDownload(key);
        assertThrows(ShareGoneException.class, () -> service.issueDownload(key));

        assertEquals(FileShare.Status.DELETED,
                repo.findBySecretKey(key).orElseThrow().getStatus());
    }

    @Test
    void pendingShareCannotBeDownloaded() {
        var init = service.initiate("pending.txt", "text/plain", 10, null, null, false);
        assertThrows(ApiExceptionHandler.UploadNotCompletedException.class,
                () -> service.issueDownload(init.share().getSecretKey()));
    }

    @Test
    void revokedShareIsGone() throws Exception {
        String key = readyShare("revoked.txt", null, false);
        service.revoke(key);
        assertThrows(ShareGoneException.class, () -> service.issueDownload(key));
    }

    @Test
    void unknownKeyIsNotFound() {
        assertThrows(ApiExceptionHandler.FileShareNotFoundException.class,
                () -> service.issueDownload("AAAAAAAAAAAAAAAAAAAAAA"));
    }
}