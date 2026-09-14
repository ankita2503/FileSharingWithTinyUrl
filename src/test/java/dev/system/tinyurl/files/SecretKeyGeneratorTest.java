package dev.system.tinyurl.files;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecretKeyGeneratorTest {

    private final SecretKeyGenerator generator = new SecretKeyGenerator();

    @Test
    void keysAreUrlSafeAnd22Chars() {
        String key = generator.generate();
        assertEquals(22, key.length());
        assertTrue(key.matches("^[A-Za-z0-9_-]{22}$"), key);
    }

    @Test
    void keysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 10_000; i++) keys.add(generator.generate());
        assertEquals(10_000, keys.size());
    }
}