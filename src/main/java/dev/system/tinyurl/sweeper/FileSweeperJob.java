package dev.system.tinyurl.sweeper;

import dev.system.tinyurl.Repository.FileShareRepository;
import dev.system.tinyurl.files.FileShare;
import dev.system.tinyurl.files.FileShareProperties;
import dev.system.tinyurl.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Removes objects and rows that are past their life.
 *
 * Three passes:
 *  1. expired, still-live shares      -> delete bytes, mark DELETED (recipients get 410)
 *  2. DELETED shares past the grace   -> delete bytes (idempotent), drop the row
 *  3. abandoned PENDING uploads       -> delete any orphaned object, drop the row
 *
 * The grace period in pass 2 must exceed the presigned download TTL, otherwise a
 * burn-after-read download already in flight would have its object removed underneath it.
 *
 * A short Redis lease ensures only one instance sweeps at a time. This is the backstop
 * that makes "temporary" true even if the app is restarted or a delete call failed;
 * the storage bucket should also carry a lifecycle rule as a final safety net.
 */
@Component
public class FileSweeperJob {

    private static final Logger log = LoggerFactory.getLogger(FileSweeperJob.class);
    public static final String LOCK_KEY = "files:sweep:lock";
    public static final Limit BATCH = Limit.of(200);

    private final FileShareRepository repo;
    private final FileStorage storage;
    private final StringRedisTemplate redis;
    private final FileShareProperties props;
    private final Clock clock;

    public FileSweeperJob(FileShareRepository repo,
                          FileStorage storage,
                          StringRedisTemplate redis,
                          FileShareProperties props,
                          Clock clock) {
        this.repo = repo;
        this.storage = storage;
        this.redis = redis;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tinyurl.files.sweep-interval:60s}")
    public void sweep() {
        Boolean locked = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", Duration.ofMinutes(2));
        if (!Boolean.TRUE.equals(locked)) return;
        try {
            int expired = sweepExpired();
            int purged = purgeDeleted();
            int abandoned = sweepAbandoned();
            if (expired + purged + abandoned > 0) {
                log.info("Sweep: {} expired, {} purged, {} abandoned", expired, purged, abandoned);
            }
        } catch (RuntimeException e) {
            log.warn("File sweep failed: {}", e.toString());
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    @Transactional
    public int sweepExpired() {
        Instant now = clock.instant();
        List<FileShare> expired = repo.findExpired(now, BATCH);
        for (FileShare share : expired) {
            deleteQuietly(share.getObjectKey());
            share.markDeleted(now);
        }
        repo.saveAll(expired);
        return expired.size();
    }

    @Transactional
    public int purgeDeleted() {
        Instant cutoff = clock.instant().minus(props.purgeGrace());
        List<FileShare> purgeable = repo.findPurgeable(cutoff, BATCH);
        for (FileShare share : purgeable) {
            deleteQuietly(share.getObjectKey());   // idempotent: object may already be gone
        }
        repo.deleteAll(purgeable);
        return purgeable.size();
    }

    @Transactional
    public int sweepAbandoned() {
        Instant cutoff = clock.instant().minus(props.abandonedAfter());
        List<FileShare> abandoned = repo.findAbandoned(cutoff, BATCH);
        for (FileShare share : abandoned) {
            deleteQuietly(share.getObjectKey());
        }
        repo.deleteAll(abandoned);
        return abandoned.size();
    }

    /** A failed delete must not abort the batch; the next sweep retries it. */
    private void deleteQuietly(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException e) {
            log.warn("Could not delete object {}: {}", objectKey, e.toString());
        }
    }
}