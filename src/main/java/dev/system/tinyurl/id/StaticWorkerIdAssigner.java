package dev.system.tinyurl.id;

public final class StaticWorkerIdAssigner implements WorkerIdAssigner {
    private final int workerId;

    public StaticWorkerIdAssigner(int workerId) { this.workerId = workerId; }

    @Override public int assign() { return workerId; }
    @Override public boolean isHealthy() { return true; }
}