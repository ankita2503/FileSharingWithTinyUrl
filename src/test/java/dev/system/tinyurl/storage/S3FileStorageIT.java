package dev.system.tinyurl.storage;

import dev.system.tinyurl.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import dev.system.tinyurl.storage.FileStorage;
import dev.system.tinyurl.storage.S3FileStorage;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class S3FileStorageIT {

    @Autowired
    FileStorage storage;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void uploadThenHeadThenDownloadThenDelete() throws Exception {
        String key = "test/" + UUID.randomUUID();
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);

        var put = storage.presignUpload(key, "text/plain");
        var putRes = http.send(HttpRequest.newBuilder(put)
                        .header("Content-Type", "text/plain")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(200, putRes.statusCode());

        var meta = storage.head(key).orElseThrow();
        assertEquals(content.length, meta.sizeBytes());

        var get = storage.presignDownload(key, "my report.pdf");
        var getRes = http.send(HttpRequest.newBuilder(get).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, getRes.statusCode());
        assertArrayEquals(content, getRes.body());
        assertTrue(getRes.headers().firstValue("content-disposition").orElse("")
                .contains("my report.pdf"));

        storage.delete(key);
        assertTrue(storage.head(key).isEmpty());
    }

    @Test
    void headOfMissingObjectIsEmpty() {
        assertTrue(storage.head("does/not/exist").isEmpty());
    }

    @Test
    void sanitizesDangerousFilenames() {
        assertEquals("evil_name.pdf", S3FileStorage.sanitizeFilename("evil\"name.pdf"));
        assertEquals("a_b.txt", S3FileStorage.sanitizeFilename("a/b.txt"));
        assertEquals("download", S3FileStorage.sanitizeFilename("  "));
    }
}
