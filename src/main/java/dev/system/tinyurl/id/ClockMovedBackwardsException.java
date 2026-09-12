package dev.system.tinyurl.id;

public class ClockMovedBackwardsException extends IllegalStateException {
    public ClockMovedBackwardsException(long lastTimestamp, long now) {
        super("Clock moved backwards: last=" + lastTimestamp + " now=" + now
                + " (delta " + (lastTimestamp - now) + "s)");
    }
}
