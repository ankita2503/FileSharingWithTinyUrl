package dev.system.tinyurl.id;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Test clock that only moves when told to. Safe to advance from another thread. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;

    public MutableClock(Instant start) { this.now = new AtomicReference<>(start); }

    public void set(Instant instant) { now.set(instant); }
    public void plusSeconds(long s) { now.updateAndGet(i -> i.plusSeconds(s)); }
    public void minusSeconds(long s) { now.updateAndGet(i -> i.minusSeconds(s)); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now.get(); }
}
