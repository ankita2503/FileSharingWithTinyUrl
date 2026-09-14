package dev.system.tinyurl.files;

import dev.system.tinyurl.Repository.FileShareRepository;
import dev.system.tinyurl.TestcontainersConfiguration;
import dev.system.tinyurl.service.FileShareService;
import dev.system.tinyurl.storage.FileStorage;
import dev.system.tinyurl.sweeper.FileSweeperJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "tinyurl.files.sweep-interval=1h",
        "tinyurl.files.purge-grace=1h",
        "tinyurl.files.abandoned-after=1h"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FileSweeperJobIT {

    @Autowired
    FileSweeperJob sweeper;
    @Autowired FileShareService service;
    @Autowired
    FileShareRepository repo;
    @Autowired FileStorage storage;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        repo.deleteAll();
        redis.delete(FileSweeperJob.LOCK_KEY);
    }

    private FileShareService.InitiateResult uploaded(String filename) throws Exception {
        var init = service.initiate(filename, "text/plain", 1024, null, null, false);
        http.send(HttpRequest.newBuilder(init.uploadUrl())
                        .header("Content-Type", init.contentType())
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(
                                "bytes".getBytes(StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.discarding());
        return init;
    }

    // --- fixtures via SQL: the entity is intentionally immutable for these fields ---

    private void expire(String secretKey) {
        update("expires_at", secretKey, 1);
    }

    private void backdateDeletedAt(String secretKey, long hours) {
        update("deleted_at", secretKey, hours);
    }

    private void backdateCreatedAt(String secretKey, long hours) {
        update("created_at", secretKey, hours);
    }

    /** Column name is from a fixed set in this class, never user input. */
    private void update(String column, String secretKey, long hoursAgo) {
        int rows = jdbc.sql("UPDATE file_shares SET " + column + " = ? WHERE secret_key = ?")
                .param(Timestamp.from(Instant.now().minus(hoursAgo, ChronoUnit.HOURS)))
                .param(secretKey)
                .update();
        assertEquals(1, rows, "fixture update should affect exactly one row");
    }

    // --- tests ---

    @Test
    void expiredShareLosesItsBytesAndIsMarkedDeleted() throws Exception {
        var init = uploaded("expiring.txt");
        String key = init.share().getSecretKey();
        String objectKey = init.share().getObjectKey();
        service.complete(key);

        expire(key);

        assertEquals(1, sweeper.sweepExpired());
        assertTrue(storage.head(objectKey).isEmpty(), "bytes should be gone");
        assertEquals(FileShare.Status.DELETED, repo.findBySecretKey(key).orElseThrow().getStatus());
    }

    @Test
    void deletedSharePastGraceIsPurgedEntirely() throws Exception {
        var init = uploaded("purgeme.txt");
        String key = init.share().getSecretKey();
        service.complete(key);
        service.revoke(key);

        assertEquals(0, sweeper.purgeDeleted(), "inside grace period the row survives");
        assertTrue(repo.findBySecretKey(key).isPresent());

        backdateDeletedAt(key, 2);

        assertEquals(1, sweeper.purgeDeleted());
        assertTrue(repo.findBySecretKey(key).isEmpty());
    }

    @Test
    void abandonedPendingUploadIsRemoved() throws Exception {
        var init = uploaded("abandoned.txt");   // uploaded but never completed
        String key = init.share().getSecretKey();
        String objectKey = init.share().getObjectKey();

        assertEquals(0, sweeper.sweepAbandoned(), "recent PENDING rows are left alone");

        backdateCreatedAt(key, 2);

        assertEquals(1, sweeper.sweepAbandoned());
        assertTrue(repo.findBySecretKey(key).isEmpty());
        assertTrue(storage.head(objectKey).isEmpty(), "orphaned object should be gone");
    }

    @Test
    void sweepSkipsWhenAnotherInstanceHoldsTheLock() throws Exception {
        var init = uploaded("locked.txt");
        String key = init.share().getSecretKey();
        service.complete(key);
        expire(key);

        redis.opsForValue().set(FileSweeperJob.LOCK_KEY, "other-instance");
        sweeper.sweep();

        assertEquals(FileShare.Status.READY, repo.findBySecretKey(key).orElseThrow().getStatus());
        redis.delete(FileSweeperJob.LOCK_KEY);
    }

    @Test
    void deleteFailureDoesNotAbortTheBatch() throws Exception {
        var a = uploaded("a.txt");
        var b = uploaded("b.txt");
        service.complete(a.share().getSecretKey());
        service.complete(b.share().getSecretKey());
        expire(a.share().getSecretKey());
        expire(b.share().getSecretKey());

        storage.delete(a.share().getObjectKey());   // already gone; delete must be idempotent

        assertEquals(2, sweeper.sweepExpired());
    }
}