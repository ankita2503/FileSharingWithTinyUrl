package dev.system.tinyurl.cache;

import java.time.Duration;
import java.util.Optional;

/** Cache port for short-key → long-URL lookups. Implementations must be safe to call concurrently. */
public interface UrlCache {

    /** @param longUrl null when {@code negative} is true */
    record CachedUrl(String longUrl, boolean negative) {
        public static CachedUrl hit(String longUrl) { return new CachedUrl(longUrl, false); }
        public static CachedUrl miss() { return new CachedUrl(null, true); }
    }

    Optional<CachedUrl> get(String shortKey);

    void put(String shortKey, String longUrl, Duration ttl);

    void putNegative(String shortKey, Duration ttl);

    void evict(String shortKey);
}
