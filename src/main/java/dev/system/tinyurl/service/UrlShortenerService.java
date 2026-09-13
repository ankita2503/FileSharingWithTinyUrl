package dev.system.tinyurl.service;

import dev.system.tinyurl.Exceptions.AliasTakenException;
import dev.system.tinyurl.Exceptions.InvalidExpiryException;
import dev.system.tinyurl.Exceptions.LinkExpiredException;
import dev.system.tinyurl.Exceptions.NotFoundException;
import dev.system.tinyurl.Repository.UrlMappingRepository;
import dev.system.tinyurl.cache.CacheProperties;
import dev.system.tinyurl.cache.UrlCache;
import dev.system.tinyurl.id.IdGenerator;
import dev.system.tinyurl.shortener.Base62Codec;
import dev.system.tinyurl.url.UrlMapping;
import dev.system.tinyurl.utility.UrlNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);

    public record ShortenResult(String shortKey, String longUrl, Instant expiresAt, boolean created) {}

    private final UrlMappingRepository repo;
    private final UrlCache cache;
    private final IdGenerator idGenerator;
    private final UrlNormalizer normalizer;
    private final CacheProperties cacheProps;
    private final Clock clock;

    public UrlShortenerService(UrlMappingRepository repo,
                               UrlCache cache,
                               IdGenerator idGenerator,
                               UrlNormalizer normalizer,
                               CacheProperties cacheProps,
                               Clock clock) {
        this.repo = repo;
        this.cache = cache;
        this.idGenerator = idGenerator;
        this.normalizer = normalizer;
        this.cacheProps = cacheProps;
        this.clock = clock;
    }

    /**
     * Creates a short link. Without a custom alias the call is idempotent: the same
     * normalized long URL returns the existing (unexpired) key.
     *
     * Known race: two concurrent first-time requests for the same URL can both insert,
     * yielding two keys for one URL. Accepted; documented in README.
     */
    @Transactional
    public ShortenResult shorten(String rawUrl, @Nullable String customAlias, @Nullable Instant expiresAt) {
        Instant now = clock.instant();
        String longUrl = normalizer.normalize(rawUrl);

        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InvalidExpiryException();
        }

        if (customAlias == null) {
            var existing = repo.findByLongUrl(longUrl);
            if (existing.isPresent() && !existing.get().isExpired(now)) {
                return toResult(existing.get(), false);
            }
        }

        long id = idGenerator.nextId();
        String shortKey = customAlias != null ? customAlias : Base62Codec.encode(id);

        var mapping = new UrlMapping(id, shortKey, longUrl, now, expiresAt);
        try {
            repo.saveAndFlush(mapping); // flush so a unique violation surfaces here, not at commit
        } catch (DataIntegrityViolationException e) {
            throw new AliasTakenException(shortKey);
        }

        log.debug("Created short key {} -> {}", shortKey, longUrl);
        return toResult(mapping, true);
    }

    /** Resolves a key to its long URL using cache-aside with negative caching. */
    @Transactional(readOnly = true)
    public String resolve(String shortKey) {
        var cached = cache.get(shortKey);
        if (cached.isPresent()) {
            if (cached.get().negative()) throw new NotFoundException(shortKey);
            return cached.get().longUrl();
        }

        var mapping = repo.findByShortKey(shortKey)
                .orElseThrow(() -> {
                    cache.putNegative(shortKey, cacheProps.negativeTtl());
                    return new NotFoundException(shortKey);
                });

        Instant now = clock.instant();
        if (mapping.isExpired(now)) {
            cache.evict(shortKey);
            throw new LinkExpiredException(shortKey);
        }

        cache.put(shortKey, mapping.getLongUrl(), ttlFor(mapping, now));
        return mapping.getLongUrl();
    }

    /** Cache TTL never outlives the link's own expiry. */
    private Duration ttlFor(UrlMapping mapping, Instant now) {
        Duration ttl = cacheProps.ttl();
        if (mapping.getExpiresAt() == null) return ttl;
        Duration untilExpiry = Duration.between(now, mapping.getExpiresAt());
        return untilExpiry.compareTo(ttl) < 0 ? untilExpiry : ttl;
    }

    private static ShortenResult toResult(UrlMapping m, boolean created) {
        return new ShortenResult(m.getShortKey(), m.getLongUrl(), m.getExpiresAt(), created);
    }

    @Transactional(readOnly = true)
    public UrlMapping metadata(String shortKey) {
        return repo.findByShortKey(shortKey).orElseThrow(() -> new NotFoundException(shortKey));
    }
}
