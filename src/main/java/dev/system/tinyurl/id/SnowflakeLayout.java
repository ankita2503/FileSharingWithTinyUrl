package dev.system.tinyurl.id;

public final class SnowflakeLayout {
    static final long EPOCH_SECONDS = 1_767_225_600L; // 2026-01-01T00:00:00Z
    static final int TIMESTAMP_BITS = 32, WORKER_BITS = 5, SEQUENCE_BITS = 10;
    static final int WORKER_SHIFT = SEQUENCE_BITS;
    static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;   // 31
    static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;  // 1023
    static final long MAX_TIMESTAMP = (1L << TIMESTAMP_BITS) - 1;
}
