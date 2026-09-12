package dev.system.tinyurl.shortener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base62CodecTest {

    @Test
    void encodesZero() {
        assertEquals("0", Base62Codec.encode(0));
    }

    @Test
    void encodesLastSingleCharacter() {
        assertEquals("Z", Base62Codec.encode(61));
    }

    @Test
    void rollsOverToTwoCharacters() {
        assertEquals("10", Base62Codec.encode(62));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 61, 62, 100_000, 1_000_000_007L, Long.MAX_VALUE})
    void decodeReversesEncode(long n) {
        assertEquals(n, Base62Codec.decode(Base62Codec.encode(n)));
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Base62Codec.decode("ab!c"));
    }

    @Test
    void rejectsNegativeNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Base62Codec.encode(-1));
    }
}