package dev.system.tinyurl.id;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class WorkerLeaseHealthIndicator implements HealthIndicator {
    private final WorkerIdAssigner assigner;
    public WorkerLeaseHealthIndicator(WorkerIdAssigner assigner) { this.assigner = assigner; }

    @Override
    public Health health() {
        return assigner.isHealthy()
                ? Health.up().withDetail("workerLease", "held").build()
                : Health.down().withDetail("workerLease", "lost").build();
    }
}