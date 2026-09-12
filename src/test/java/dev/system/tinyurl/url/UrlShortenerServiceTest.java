package dev.system.tinyurl.url;


import dev.system.tinyurl.Exceptions.AliasTakenException;
import dev.system.tinyurl.Exceptions.InvalidExpiryException;
import dev.system.tinyurl.Exceptions.LinkExpiredException;
import dev.system.tinyurl.Exceptions.NotFoundException;
import dev.system.tinyurl.Repository.UrlMappingRepository;
import dev.system.tinyurl.cache.CacheProperties;
import dev.system.tinyurl.cache.UrlCache;
import dev.system.tinyurl.id.IdGenerator;
import dev.system.tinyurl.id.MutableClock;
import dev.system.tinyurl.service.UrlShortenerService;
import dev.system.tinyurl.shortener.Base62Codec;
import dev.system.tinyurl.utility.UrlNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final long ID = 123_456_789L;
    private static final String KEY = Base62Codec.encode(ID);
    private static final String URL = "https://example.com/page";

    @Mock
    UrlMappingRepository repo;
    @Mock
    UrlCache cache;
    @Mock
    IdGenerator idGenerator;

    private MutableClock clock;
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new UrlShortenerService(repo, cache, idGenerator, new UrlNormalizer(),
                new CacheProperties(Duration.ofHours(24), Duration.ofMinutes(5)), clock);
    }

    // ---- shorten ----

    @Test
    void shortenCreatesNewMappingWithBase62Key() {
        when(repo.findByLongUrl(URL)).thenReturn(Optional.empty());
        when(idGenerator.nextId()).thenReturn(ID);

        var result = service.shorten(URL, null, null);

        assertEquals(KEY, result.shortKey());
        assertTrue(result.created());
        verify(repo).saveAndFlush(argThat(m -> m.getId() == ID && m.getShortKey().equals(KEY)));
    }

    @Test
    void shortenIsIdempotentForSameNormalizedUrl() {
        var existing = new UrlMapping(ID, KEY, URL, NOW.minusSeconds(60), null);
        when(repo.findByLongUrl(URL)).thenReturn(Optional.of(existing));

        var result = service.shorten("HTTPS://Example.com/page", null, null);

        assertEquals(KEY, result.shortKey());
        assertFalse(result.created());
        verify(repo, never()).saveAndFlush(any());
        verifyNoInteractions(idGenerator);
    }

    @Test
    void shortenIgnoresExpiredExistingMapping() {
        var expired = new UrlMapping(1L, "old", URL, NOW.minusSeconds(600), NOW.minusSeconds(1));
        when(repo.findByLongUrl(URL)).thenReturn(Optional.of(expired));
        when(idGenerator.nextId()).thenReturn(ID);

        var result = service.shorten(URL, null, null);

        assertEquals(KEY, result.shortKey());
        assertTrue(result.created());
    }

    @Test
    void shortenWithCustomAliasSkipsIdempotencyLookup() {
        when(idGenerator.nextId()).thenReturn(ID);

        var result = service.shorten(URL, "mylink", null);

        assertEquals("mylink", result.shortKey());
        verify(repo, never()).findByLongUrl(any());
        verify(repo).saveAndFlush(argThat(m -> m.getId() == ID && m.getShortKey().equals("mylink")));
    }

    @Test
    void shortenMapsUniqueViolationToAliasTaken() {
        when(idGenerator.nextId()).thenReturn(ID);
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(AliasTakenException.class, () -> service.shorten(URL, "taken", null));
    }

    @Test
    void shortenRejectsPastExpiry() {
        assertThrows(InvalidExpiryException.class,
                () -> service.shorten(URL, null, NOW.minusSeconds(1)));
        verifyNoInteractions(repo, idGenerator);
    }

    // ---- resolve ----

    @Test
    void resolveReturnsFromCacheWithoutTouchingRepo() {
        when(cache.get(KEY)).thenReturn(Optional.of(UrlCache.CachedUrl.hit(URL)));

        assertEquals(URL, service.resolve(KEY));
        verifyNoInteractions(repo);
    }

    @Test
    void resolveNegativeCacheThrowsNotFoundWithoutRepo() {
        when(cache.get(KEY)).thenReturn(Optional.of(UrlCache.CachedUrl.miss()));

        assertThrows(NotFoundException.class, () -> service.resolve(KEY));
        verifyNoInteractions(repo);
    }

    @Test
    void resolveMissLoadsFromRepoAndCaches() {
        when(cache.get(KEY)).thenReturn(Optional.empty());
        when(repo.findByShortKey(KEY)).thenReturn(Optional.of(new UrlMapping(ID, KEY, URL, NOW, null)));

        assertEquals(URL, service.resolve(KEY));
        verify(cache).put(KEY, URL, Duration.ofHours(24));
    }

    @Test
    void resolveCacheTtlIsCappedByLinkExpiry() {
        when(cache.get(KEY)).thenReturn(Optional.empty());
        when(repo.findByShortKey(KEY))
                .thenReturn(Optional.of(new UrlMapping(ID, KEY, URL, NOW, NOW.plusSeconds(600))));

        service.resolve(KEY);

        verify(cache).put(KEY, URL, Duration.ofSeconds(600));
    }

    @Test
    void resolveUnknownKeyStoresNegativeEntry() {
        when(cache.get(KEY)).thenReturn(Optional.empty());
        when(repo.findByShortKey(KEY)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.resolve(KEY));
        verify(cache).putNegative(KEY, Duration.ofMinutes(5));
    }

    @Test
    void resolveExpiredLinkEvictsAndThrows() {
        when(cache.get(KEY)).thenReturn(Optional.empty());
        when(repo.findByShortKey(KEY))
                .thenReturn(Optional.of(new UrlMapping(ID, KEY, URL, NOW.minusSeconds(100), NOW.minusSeconds(1))));

        assertThrows(LinkExpiredException.class, () -> service.resolve(KEY));
        verify(cache).evict(KEY);
        verify(cache, never()).put(any(), any(), any());
    }
}