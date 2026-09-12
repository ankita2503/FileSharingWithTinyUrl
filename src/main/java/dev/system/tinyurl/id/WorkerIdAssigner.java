package dev.system.tinyurl.id;

public interface WorkerIdAssigner {
    /** Called once at startup. Must return a value in [0, MAX_WORKER_ID]. */
    int assign();

    /** False once this instance can no longer prove it owns its worker id. */
    boolean isHealthy();
}