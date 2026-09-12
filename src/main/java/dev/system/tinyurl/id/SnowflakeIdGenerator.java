package dev.system.tinyurl.id;

import java.time.Clock;

import static dev.system.tinyurl.id.SnowflakeLayout.*;

/**
 * Snowflake-style id generator. Thread-safe; one instance per JVM/worker.
 * Ids are strictly increasing per worker and unique across workers as long
 * as each running instance has a distinct workerId (see WorkerIdAssigner).
 */
public final class SnowflakeIdGenerator implements IdGenerator {

    /** Max tolerated backwards clock drift before we refuse to issue ids. */
    static final long MAX_BACKWARD_DRIFT_SECONDS = 5;

    public record Parts(long timestampSeconds, int workerId, int sequence) {}

    private final int workerId;
    private final Clock clock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(int workerId, Clock clock) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId must be in [0," + MAX_WORKER_ID + "], got " + workerId);
        }
        this.workerId = workerId;
        this.clock = clock;
    }

    @Override
    public synchronized long nextId() {
        long now = currentTimestamp();

        if (now < lastTimestamp) {
            long drift = lastTimestamp - now;
            if (drift > MAX_BACKWARD_DRIFT_SECONDS) {
                throw new ClockMovedBackwardsException(lastTimestamp, now);
            }
            now = lastTimestamp; // keep issuing in the last known second
        }

        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                now = waitUntilAfter(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        if (now > MAX_TIMESTAMP) {
            throw new IllegalStateException("Timestamp bits exhausted");
        }

        lastTimestamp = now;
        return (now << TIMESTAMP_SHIFT)
                | ((long) workerId << WORKER_SHIFT)
                | sequence;
    }

    public static Parts decode(long id) {
        long ts = (id >>> TIMESTAMP_SHIFT) & MAX_TIMESTAMP;
        int worker = (int) ((id >>> WORKER_SHIFT) & MAX_WORKER_ID);
        int seq = (int) (id & MAX_SEQUENCE);
        return new Parts(ts, worker, seq);
    }

    private long currentTimestamp() {
        return clock.instant().getEpochSecond() - EPOCH_SECONDS;
    }

    private long waitUntilAfter(long timestamp) {
        long now = currentTimestamp();
        while (now <= timestamp) {
            Thread.onSpinWait();
            now = currentTimestamp();
        }
        return now;
    }
}