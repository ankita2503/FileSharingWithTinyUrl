package dev.system.tinyurl.service;

import dev.system.tinyurl.Exceptions.*;
import dev.system.tinyurl.Repository.FileShareRepository;
import dev.system.tinyurl.files.FileShare;
import dev.system.tinyurl.files.FileShareProperties;
import dev.system.tinyurl.files.SecretKeyGenerator;
import dev.system.tinyurl.id.IdGenerator;
import dev.system.tinyurl.storage.FileStorage;
import dev.system.tinyurl.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class FileShareService {

    private static final Logger log = LoggerFactory.getLogger(FileShareService.class);

    public record InitiateResult(FileShare share, URI uploadUrl, String contentType) {
    }

    private final FileShareRepository repo;
    private final FileStorage storage;
    private final IdGenerator idGenerator;
    private final SecretKeyGenerator secretKeys;
    private final FileShareProperties fileProps;
    private final StorageProperties storageProps;
    private final Clock clock;

    public FileShareService(FileShareRepository repo,
                            FileStorage storage,
                            IdGenerator idGenerator,
                            SecretKeyGenerator secretKeys,
                            FileShareProperties fileProps,
                            StorageProperties storageProps,
                            Clock clock) {
        this.repo = repo;
        this.storage = storage;
        this.idGenerator = idGenerator;
        this.secretKeys = secretKeys;
        this.fileProps = fileProps;
        this.storageProps = storageProps;
        this.clock = clock;
    }

    /**
     * Reserves a share and returns a presigned PUT the client uploads to directly.
     * The row is PENDING until {@link #complete} confirms the bytes arrived; abandoned
     * PENDING rows are swept later.
     */
    @Transactional
    public InitiateResult initiate(String filename,
                                   @Nullable String contentType,
                                   long declaredSize,
                                   @Nullable Duration ttl,
                                   @Nullable Integer maxDownloads,
                                   boolean burnAfterRead) {

        if (filename == null || filename.isBlank()) {
            throw new ApiExceptionHandler.InvalidShareRequestException("filename is required");
        }
        if (declaredSize <= 0) {
            throw new ApiExceptionHandler.InvalidShareRequestException("sizeBytes must be positive");
        }
        if (declaredSize > storageProps.maxFileSizeBytes()) {
            throw new ApiExceptionHandler.FileTooLargeException(declaredSize, storageProps.maxFileSizeBytes());
        }
        if (maxDownloads != null && maxDownloads < 1) {
            throw new ApiExceptionHandler.InvalidShareRequestException("maxDownloads must be at least 1");
        }

        Duration effectiveTtl = ttl == null ? fileProps.defaultTtl() : ttl;
        if (effectiveTtl.isNegative() || effectiveTtl.isZero()) {
            throw new ApiExceptionHandler.InvalidShareRequestException("ttl must be positive");
        }
        if (effectiveTtl.compareTo(fileProps.maxTtl()) > 0) {
            throw new ApiExceptionHandler.InvalidShareRequestException("ttl exceeds maximum of " + fileProps.maxTtl());
        }

        Instant now = clock.instant();
        String objectKey = "uploads/" + UUID.randomUUID();

        // Burn-after-read implies exactly one download. Integer.valueOf is required:
        // a ternary mixing int and Integer unboxes the null branch and throws NPE.
        Integer effectiveMaxDownloads = burnAfterRead ? Integer.valueOf(1) : maxDownloads;

        var share = new FileShare(
                idGenerator.nextId(),
                secretKeys.generate(),
                objectKey,
                filename.trim(),
                now,
                now.plus(effectiveTtl),
                effectiveMaxDownloads,
                burnAfterRead);

        repo.saveAndFlush(share);
        String effectiveContentType =
                (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;

        URI uploadUrl = storage.presignUpload(objectKey, effectiveContentType);

        log.debug("Initiated share {} -> {}", share.getSecretKey(), objectKey);
        return new InitiateResult(share, uploadUrl, effectiveContentType);
    }

    /**
     * Confirms an upload. Size and content type are read from storage rather than
     * trusted from the client, and an over-limit object is deleted rather than kept.
     */
    @Transactional
    public FileShare complete(String secretKey) {
        var share = repo.findBySecretKey(secretKey)
                .orElseThrow(() -> new ApiExceptionHandler.FileShareNotFoundException(secretKey));

        if (share.getStatus() == FileShare.Status.READY) {
            return share;                                    // idempotent
        }
        if (share.getStatus() == FileShare.Status.DELETED) {
            throw new ApiExceptionHandler.FileShareNotFoundException(secretKey);
        }

        var stored = storage.head(share.getObjectKey())
                .orElseThrow(() -> new ApiExceptionHandler.UploadNotCompletedException(secretKey));

        if (stored.sizeBytes() > storageProps.maxFileSizeBytes()) {
            storage.delete(share.getObjectKey());
            share.markDeleted();
            repo.saveAndFlush(share);
            throw new ApiExceptionHandler.FileTooLargeException(stored.sizeBytes(), storageProps.maxFileSizeBytes());
        }

        share.markReady(stored.sizeBytes(), stored.contentType());
        repo.saveAndFlush(share);
        log.info("Share {} ready ({} bytes)", secretKey, stored.sizeBytes());
        return share;
    }

    @Transactional(readOnly = true)
    public FileShare metadata(String secretKey) {
        return repo.findBySecretKey(secretKey)
                .orElseThrow(() -> new ApiExceptionHandler.FileShareNotFoundException(secretKey));
    }

    /**
     * Sender-initiated revocation: delete the object immediately.
     */
    @Transactional
    public void revoke(String secretKey) {
        var share = repo.findBySecretKey(secretKey)
                .orElseThrow(() -> new ApiExceptionHandler.FileShareNotFoundException(secretKey));
        if (share.getStatus() != FileShare.Status.DELETED) {
            storage.delete(share.getObjectKey());
            share.markDeleted();
            repo.saveAndFlush(share);
            log.info("Share {} revoked", secretKey);
        }
    }

    /**
     * Issues a short-lived presigned GET and counts the download.
     * <p>
     * Burn-after-read and exhausted shares are marked DELETED immediately so no further
     * URLs can be issued, but the object itself is left for the sweeper to remove after
     * a grace period. Deleting the bytes here would mean a dropped connection destroys
     * the file: the link should be single-*attempt*, not single-*success*.
     */
    @Transactional
    public URI issueDownload(String secretKey) {
        var share = repo.findBySecretKey(secretKey)
                .orElseThrow(() -> new ApiExceptionHandler.FileShareNotFoundException(secretKey));

        Instant now = clock.instant();

        if (share.getStatus() == FileShare.Status.PENDING) {
            throw new ApiExceptionHandler.UploadNotCompletedException(secretKey);
        }
        if (share.getStatus() == FileShare.Status.DELETED
                || share.isExpired(now)
                || share.isExhausted()) {
            throw new ShareGoneException(secretKey);
        }

        share.recordDownload();
        URI url = storage.presignDownload(share.getObjectKey(), share.getFilename());

        if (share.isBurnAfterRead() || share.isExhausted()) {
            share.markDeleted(now);
            log.info("Share {} consumed after download {}", secretKey, share.getDownloadCount());
        }

        repo.saveAndFlush(share);
        return url;
    }
}