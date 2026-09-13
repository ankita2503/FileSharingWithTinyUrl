package dev.system.tinyurl.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tinyurl.app")
public record AppProperties(String baseUrl) {
    public AppProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8080";
        baseUrl = baseUrl.replaceAll("/+$", "");
    }
}
