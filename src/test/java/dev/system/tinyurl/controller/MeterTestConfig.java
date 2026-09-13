package dev.system.tinyurl.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
class MeterTestConfig {
    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}