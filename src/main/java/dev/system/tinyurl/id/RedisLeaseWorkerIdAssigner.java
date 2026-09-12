package dev.system.tinyurl.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.system.tinyurl.id.SnowflakeLayout.MAX_WORKER_ID;

/**
 * Leases a worker id slot from Redis using SET NX EX, renews it on a heartbeat,
 * and releases it on shutdown. Renewal and release are compare-and-set Lua
 * scripts so an instance can never touch a lease it no longer owns.
 */
public final class RedisLeaseWorkerIdAssigner implements WorkerIdAssigner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RedisLeaseWorkerIdAssigner.class);
    static final String KEY_PREFIX = "tinyurl:worker:lease:";

    private final StringRedisTemplate redis;
    private final Duration leaseTtl;
    private final String instanceId;
    private final RedisScript<Long> renewScript = script("redis/renew_if_owner.lua");
    private final RedisScript<Long> releaseScript = script("redis/delete_if_owner.lua");
    private final AtomicBoolean healthy = new AtomicBoolean(false);

    private volatile String leaseKey;
    private volatile int workerId = -1;

    public RedisLeaseWorkerIdAssigner(StringRedisTemplate redis, Duration leaseTtl) {
        this.redis = redis;
        this.leaseTtl = leaseTtl;
        this.instanceId = hostname() + "-" + UUID.randomUUID();
    }

    @Override
    public int assign() {
        for (int slot = 0; slot <= MAX_WORKER_ID; slot++) {
            String key = KEY_PREFIX + slot;
            Boolean acquired = redis.opsForValue().setIfAbsent(key, instanceId, leaseTtl);
            if (Boolean.TRUE.equals(acquired)) {
                leaseKey = key;
                workerId = slot;
                healthy.set(true);
                log.info("Acquired worker id {} (instance {})", slot, instanceId);
                return slot;
            }
        }
        throw new IllegalStateException(
                "No free worker id slots in [0," + MAX_WORKER_ID + "]; scale down or widen WORKER_BITS");
    }

    /** Scheduled from IdConfiguration; also callable directly in tests. */
    public void heartbeat() {
        if (leaseKey == null) return;
        Long renewed = redis.execute(renewScript, List.of(leaseKey), instanceId,
                String.valueOf(leaseTtl.toSeconds()));
        boolean ok = renewed != null && renewed == 1L;
        if (!ok && healthy.get()) {
            log.error("Lost worker lease {} (instance {}); marking unhealthy", leaseKey, instanceId);
        }
        healthy.set(ok);
    }

    @Override
    public boolean isHealthy() { return healthy.get(); }

    public int workerId() { return workerId; }

    @Override
    public void destroy() {
        if (leaseKey == null) return;
        Long released = redis.execute(releaseScript, List.of(leaseKey), instanceId);
        log.info("Released worker lease {}: {}", leaseKey, released != null && released == 1L);
    }

    private static RedisScript<Long> script(String path) {
        var s = new DefaultRedisScript<Long>();
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        s.setResultType(Long.class);
        return s;
    }

    private static String hostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown-host"; }
    }
}