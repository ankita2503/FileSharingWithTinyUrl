package dev.system.tinyurl.url;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "urls")
public class UrlMapping implements Persistable<Long> {

    @Id
    private Long id;

    @Column(name = "short_key", nullable = false, unique = true, length = 8)
    private String shortKey;

    @Column(name = "long_url", nullable = false, columnDefinition = "text")
    private String longUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    /** True until persisted/loaded; lets Spring Data INSERT without a preceding SELECT. */
    @Transient
    private boolean isNew = true;

    protected UrlMapping() {}

    public UrlMapping(long id, String shortKey, String longUrl, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.shortKey = shortKey;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.clickCount = 0;
    }

    @PostPersist
    @PostLoad
    void markNotNew() { this.isNew = false; }

    @Override public Long getId() { return id; }
    @Override public boolean isNew() { return isNew; }

    public String getShortKey() { return shortKey; }
    public String getLongUrl() { return longUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getClickCount() { return clickCount; }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}