package dev.system.tinyurl.url;

import dev.system.tinyurl.Exceptions.AliasTakenException;
import dev.system.tinyurl.Exceptions.LinkExpiredException;
import dev.system.tinyurl.Exceptions.NotFoundException;
import dev.system.tinyurl.Repository.UrlMappingRepository;
import dev.system.tinyurl.TestcontainersConfiguration;
import dev.system.tinyurl.cache.RedisUrlCache;
import dev.system.tinyurl.service.UrlShortenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UrlShortenerServiceIT {

    @Autowired
    UrlShortenerService service;
    @Autowired
    StringRedisTemplate redis;
    @MockitoSpyBean
    UrlMappingRepository repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
        var conn = redis.getConnectionFactory().getConnection();
        conn.serverCommands().flushDb();
        conn.close();
        clearInvocations(repo);
    }

    @Test
    void createThenResolveHitsDbOnceThenServesFromRedis() {
        var created = service.shorten("https://example.com/a", null, null);

        assertEquals("https://example.com/a", service.resolve(created.shortKey()));
        assertEquals("https://example.com/a",
                redis.opsForValue().get(RedisUrlCache.KEY_PREFIX + created.shortKey()));

        clearInvocations(repo);
        assertEquals("https://example.com/a", service.resolve(created.shortKey()));
        verify(repo, never()).findByShortKey(any());
    }

    @Test
    void sameUrlTwiceReturnsSameKey() {
        var first = service.shorten("https://example.com/b", null, null);
        var second = service.shorten("HTTPS://EXAMPLE.com/b", null, null);

        assertEquals(first.shortKey(), second.shortKey());
        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(1, repo.count());
    }

    @Test
    void duplicateCustomAliasIsRejected() {
        service.shorten("https://example.com/c", "promo", null);
        assertThrows(AliasTakenException.class,
                () -> service.shorten("https://example.com/d", "promo", null));
    }

    @Test
    void unknownKeyWritesNegativeEntry() {
        assertThrows(NotFoundException.class, () -> service.resolve("nope1234"));
        assertEquals(RedisUrlCache.NEGATIVE_SENTINEL,
                redis.opsForValue().get(RedisUrlCache.KEY_PREFIX + "nope1234"));

        clearInvocations(repo);
        assertThrows(NotFoundException.class, () -> service.resolve("nope1234"));
        verify(repo, never()).findByShortKey(any());
    }

    @Test
    void expiredLinkReturnsExpired() {
        // insert directly with a past expiry; shorten() would reject it
        var mapping = new UrlMapping(42L, "expired1", "https://example.com/e",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(1));
        repo.saveAndFlush(mapping);

        assertThrows(LinkExpiredException.class, () -> service.resolve("expired1"));
        assertNull(redis.opsForValue().get(RedisUrlCache.KEY_PREFIX + "expired1"));
    }
}
