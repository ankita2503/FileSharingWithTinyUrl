package dev.system.tinyurl.files;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * A temporary, link-addressable file share.
 *
 * Lifecycle: PENDING (presigned upload issued) -> READY (bytes confirmed in storage)
 * -> DELETED (object removed; row retained briefly so the recipient gets 410 rather
 * than 404, which distinguishes "expired" from "never existed").
 *
 * expires_at is mandatory: every share must die. Unlike short links, there is no
 * "keep forever" option, because the storage cost and the exposure are unbounded.
 */
@Entity
@Table(name = "file_shares")
public class FileShare implements Persistable<Long> {

    public enum Status { PENDING, READY, DELETED }

    @Id
    private Long id;

    /** 128-bit unguessable capability token that appears in the share URL. */
    @Column(name = "secret_key", nullable = false, unique = true, length = 32)
    private String secretKey;

    /** Storage location. Deliberately unrelated to secretKey: identity != location. */
    @Column(name = "object_key", nullable = false, unique = true, length = 128)
    private String objectKey;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    /** Populated from storage metadata on completion, not from the client's claim. */
    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    @Column(name = "burn_after_read", nullable = false)
    private boolean burnAfterRead;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Marks the share unusable immediately; the bytes are removed later by the sweeper. */
    public void markDeleted(Instant now) {
        this.status = Status.DELETED;
        this.deletedAt = now;
    }

    public Instant getDeletedAt() { return deletedAt; }

    /** Remaining downloads, or null when unlimited. */
    public Integer remainingDownloads() {
        return maxDownloads == null ? null : Math.max(0, maxDownloads - downloadCount);
    }

    @Transient
    private boolean isNew = true;

    protected FileShare() {}

    public FileShare(long id, String secretKey, String objectKey, String filename,
                     Instant createdAt, Instant expiresAt,
                     Integer maxDownloads, boolean burnAfterRead) {
        this.id = id;
        this.secretKey = secretKey;
        this.objectKey = objectKey;
        this.filename = filename;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.maxDownloads = maxDownloads;
        this.burnAfterRead = burnAfterRead;
        this.status = Status.PENDING;
        this.downloadCount = 0;
    }

    @PostPersist
    @PostLoad
    void markNotNew() { this.isNew = false; }

    // ---- behaviour ----

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isExhausted() {
        return maxDownloads != null && downloadCount >= maxDownloads;
    }

    public boolean isDownloadable(Instant now) {
        return status == Status.READY && !isExpired(now) && !isExhausted();
    }

    /** Called once the bytes are confirmed present; size and type come from storage. */
    public void markReady(long sizeBytes, String contentType) {
        this.status = Status.READY;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
    }

    public void markDeleted() {
        this.status = Status.DELETED;
    }

    public int recordDownload() {
        return ++this.downloadCount;
    }

    // ---- accessors ----

    @Override public Long getId() { return id; }
    @Override public boolean isNew() { return isNew; }

    public String getSecretKey() { return secretKey; }
    public String getObjectKey() { return objectKey; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public Status getStatus() { return status; }
    public Integer getMaxDownloads() { return maxDownloads; }
    public int getDownloadCount() { return downloadCount; }
    public boolean isBurnAfterRead() { return burnAfterRead; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}