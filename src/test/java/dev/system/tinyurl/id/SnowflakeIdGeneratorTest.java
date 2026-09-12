package dev.system.tinyurl.id;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;

import static dev.system.tinyurl.id.SnowflakeLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    private static final Instant START = Instant.ofEpochSecond(EPOCH_SECONDS + 1_000);

    private MutableClock clock;
    private SnowflakeIdGenerator gen;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        gen = new SnowflakeIdGenerator(7, clock);
    }

    @Test
    void idsAreStrictlyIncreasingWithinASecond() {
        long prev = gen.nextId();
        for (int i = 0; i < 100; i++) {
            long next = gen.nextId();
            assertTrue(next > prev, "id " + next + " not > " + prev);
            prev = next;
        }
    }

    @Test
    void decodeRoundTrips() {
        long id = 0;
        for (int i = 0; i < 4; i++) id = gen.nextId();
        var parts = SnowflakeIdGenerator.decode(id);
        assertEquals(1_000, parts.timestampSeconds());
        assertEquals(7, parts.workerId());
        assertEquals(3, parts.sequence());
    }

    @Test
    void newSecondResetsSequence() {
        gen.nextId(); gen.nextId();
        clock.plusSeconds(1);
        var parts = SnowflakeIdGenerator.decode(gen.nextId());
        assertEquals(1_001, parts.timestampSeconds());
        assertEquals(0, parts.sequence());
    }

    @Test
    void sequenceOverflowWaitsForNextSecond() {
        for (int i = 0; i <= MAX_SEQUENCE; i++) gen.nextId(); // 1024 ids: seq 0..1023

        var advancer = CompletableFuture.runAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            clock.plusSeconds(1);
        });

        long id = assertTimeoutPreemptively(Duration.ofSeconds(2), gen::nextId);
        var parts = SnowflakeIdGenerator.decode(id);
        assertEquals(1_001, parts.timestampSeconds());
        assertEquals(0, parts.sequence());
        advancer.join();
    }

    @Test
    void smallClockRegressionIsTolerated() {
        long first = gen.nextId();
        clock.minusSeconds(2);
        long second = gen.nextId();
        assertTrue(second > first);
        assertEquals(1_000, SnowflakeIdGenerator.decode(second).timestampSeconds());
    }

    @Test
    void largeClockRegressionThrows() {
        gen.nextId();
        clock.minusSeconds(60);
        assertThrows(ClockMovedBackwardsException.class, gen::nextId);
    }

    @Test
    void rejectsWorkerIdOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1, clock));
        assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator((int) MAX_WORKER_ID + 1, clock));
    }

    @Test
    void concurrentCallsProduceUniqueIds() throws Exception {
        var realGen = new SnowflakeIdGenerator(3, Clock.systemUTC());
        int threads = 8, perThread = 500;
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            var futures = new CompletableFuture[threads];
            for (int t = 0; t < threads; t++) {
                futures[t] = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < perThread; i++) ids.add(realGen.nextId());
                }, pool);
            }
            CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        }
        assertEquals(threads * perThread, ids.size());
    }

    @Test
    void maxIdFitsInEightBase62Chars() {
        long maxId = (MAX_TIMESTAMP << TIMESTAMP_SHIFT)
                | (MAX_WORKER_ID << WORKER_SHIFT) | MAX_SEQUENCE;
        assertTrue(dev.system.tinyurl.shortener.Base62Codec.encode(maxId).length() <= 8);
    }
}
