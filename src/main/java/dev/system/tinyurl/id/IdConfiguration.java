package dev.system.tinyurl.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

@Configuration
public class IdConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IdConfiguration.class);

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    WorkerIdAssigner workerIdAssigner(IdProperties props, StringRedisTemplate redis) {
        if (props.workerId() != null) {
            log.info("Using static worker id {}", props.workerId());
            return new StaticWorkerIdAssigner(props.workerId());
        }
        return new RedisLeaseWorkerIdAssigner(redis, props.leaseTtl());
    }

    @Bean
    IdGenerator idGenerator(WorkerIdAssigner assigner, Clock clock) {
        int workerId = assigner.assign();
        log.info("Snowflake worker id {}", workerId);
        return new SnowflakeIdGenerator(workerId, clock);
    }

    @Bean
    WorkerLeaseHeartbeat workerLeaseHeartbeat(WorkerIdAssigner assigner) {
        return new WorkerLeaseHeartbeat(assigner);
    }

    /** Separate bean so @Scheduled works regardless of which assigner is active. */
    static class WorkerLeaseHeartbeat {
        private final WorkerIdAssigner assigner;
        WorkerLeaseHeartbeat(WorkerIdAssigner assigner) { this.assigner = assigner; }

        @Scheduled(fixedDelayString = "${tinyurl.id.heartbeat:20s}")
        void tick() {
            if (assigner instanceof RedisLeaseWorkerIdAssigner lease) lease.heartbeat();
        }
    }
}