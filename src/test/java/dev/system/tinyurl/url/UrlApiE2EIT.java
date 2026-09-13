package dev.system.tinyurl.url;

import dev.system.tinyurl.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;   // Boot 4 alt: org.springframework.boot.web.server.test.LocalServerPort
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tinyurl.app.base-url=http://short.test")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UrlApiE2EIT {

    @LocalServerPort int port;
    private RestClient client;

    @BeforeEach
    void setUp() {
        var jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(jdk))
                .defaultStatusHandler(s -> true, (req, res) -> {}) // don't throw on 4xx/5xx
                .build();
    }

    @Test
    void createThenFollowRedirect() {
        var create = client.post().uri("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("longUrl", "https://spring.io/projects/spring-boot"))
                .retrieve().toEntity(Map.class);

        assertEquals(HttpStatus.CREATED, create.getStatusCode());
        String shortKey = (String) create.getBody().get("shortKey");
        assertNotNull(shortKey);
        assertEquals("http://short.test/" + shortKey, create.getHeaders().getFirst("Location"));
        assertEquals("http://short.test/" + shortKey, create.getBody().get("shortUrl"));

        var redirect = client.get().uri("/" + shortKey).retrieve().toBodilessEntity();
        assertEquals(HttpStatus.FOUND, redirect.getStatusCode());
        assertEquals("https://spring.io/projects/spring-boot", redirect.getHeaders().getFirst("Location"));

        var meta = client.get().uri("/api/v1/urls/" + shortKey).retrieve().toEntity(Map.class);
        assertEquals(HttpStatus.OK, meta.getStatusCode());
        assertEquals("https://spring.io/projects/spring-boot", meta.getBody().get("longUrl"));
    }

    @Test
    void unknownKeyIsProblemJson404() {
        var res = client.get().uri("/nope0000").retrieve().toEntity(Map.class);
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertEquals("NOT_FOUND", res.getBody().get("code"));
    }

    @Test
    void duplicateAliasIs409() {
        var body = Map.of("longUrl", "https://example.com/x", "customAlias", "e2ealias");
        client.post().uri("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity();
        var second = client.post().uri("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("longUrl", "https://example.com/y", "customAlias", "e2ealias"))
                .retrieve().toEntity(Map.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }
}