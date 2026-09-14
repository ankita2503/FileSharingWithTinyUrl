package dev.system.tinyurl;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }

    @Bean
    GenericContainer<?> minio() {
        return new GenericContainer<>("quay.io/minio/minio:RELEASE.2025-02-18T16-25-55Z")
                .withCommand("server", "/data")
                .withEnv("MINIO_ROOT_USER", "minioadmin")
                .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                .withExposedPorts(9000);
    }

    @Bean
    DynamicPropertyRegistrar minioProperties(GenericContainer<?> minio) {
        return registry -> {
            String url = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
            registry.add("tinyurl.storage.endpoint", () -> url);
            registry.add("tinyurl.storage.public-endpoint", () -> url);
        };
    }
}