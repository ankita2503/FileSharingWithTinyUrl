package dev.system.tinyurl.url;

import dev.system.tinyurl.Exceptions.InvalidUrlException;
import dev.system.tinyurl.Exceptions.NotFoundException;
import dev.system.tinyurl.utility.UrlNormalizer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlNormalizerTest {

    private final UrlNormalizer normalizer = new UrlNormalizer();

    @ParameterizedTest
    @CsvSource({
            "HTTPS://Example.com/A/b,            https://example.com/A/b",
            "  https://example.com  ,            https://example.com/",
            "http://example.com:80/x,            http://example.com/x",
            "https://example.com:443/x,          https://example.com/x",
            "https://example.com:8443/x,         https://example.com:8443/x",
            "https://example.com/p?q=1&r=2#top,  https://example.com/p?q=1&r=2",
            "https://example.com/p?q=1,          https://example.com/p?q=1",
            "http://user:pw@example.com/,        http://user:pw@example.com/"
    })
    void normalizes(String input, String expected) {
        assertEquals(expected, normalizer.normalize(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ftp://example.com/file", "example.com/no-scheme",
            "https://", "javascript:alert(1)", "http://exa mple.com"})
    void rejectsInvalid(String input) {
        assertThrows(InvalidUrlException.class, () -> normalizer.normalize(input));
    }

    @org.junit.jupiter.api.Test
    void rejectsNullAndTooLong() {
        assertThrows(InvalidUrlException.class, () -> normalizer.normalize(null));
        assertThrows(InvalidUrlException.class,
                () -> normalizer.normalize("https://example.com/" + "a".repeat(2100)));
    }
}