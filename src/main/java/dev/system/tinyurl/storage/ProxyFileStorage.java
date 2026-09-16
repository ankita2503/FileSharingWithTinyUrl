package dev.system.tinyurl.storage;

import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Replaces presigned URLs with app-proxied ones, for environments that expose a
 * single public port (the Hugging Face Spaces demo) and therefore cannot reach the
 * object store directly.
 *
 * A presigned S3 URL cannot simply be forwarded: SigV4 signs the Host header and
 * path, so a proxy under a different hostname breaks the signature. Instead this
 * class mints its own opaque capability token, stores the object key against it in
 * Redis with the same TTL the presigned URL would have had, and streams the bytes
 * itself using the application's credentials.
 *
 * The trade-off is explicit: file bytes now flow through the application, which the
 * presigned design exists to avoid. Against R2 or S3 this decorator is disabled and
 * the client talks to storage directly.
 */
@Component
@Primary
@ConditionalOnProperty("tinyurl.storage.proxy-public-url")
public class ProxyFileStorage implements FileStorage {

    static final String TOKEN_PREFIX = "storage:token:";

    private final S3FileStorage delegate;
    private final S3Client s3;
    private final StringRedisTemplate redis;
    private final StorageProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public record TokenData(String objectKey, String filename, String contentType, boolean upload) {}

    public ProxyFileStorage(S3FileStorage delegate, S3Client s3,
                            StringRedisTemplate redis, StorageProperties props) {
        this.delegate = delegate;
        this.s3 = s3;
        this.redis = redis;
        this.props = props;
    }

    @Override
    public URI presignUpload(String objectKey, String contentType) {
        String token = mint(new TokenData(objectKey, null, contentType, true), props.uploadUrlTtl());
        return URI.create(props.proxyPublicUrl() + "/u/" + token);
    }

    @Override
    public URI presignDownload(String objectKey, String filename) {
        String token = mint(new TokenData(objectKey, filename, null, false), props.downloadUrlTtl());
        return URI.create(props.proxyPublicUrl() + "/d/" + token);
    }

    @Override public Optional<StoredObject> head(String objectKey) { return delegate.head(objectKey); }
    @Override public void delete(String objectKey) { delegate.delete(objectKey); }

    // ---- used by StorageProxyController ----

    public Optional<TokenData> resolve(String token, boolean upload) {
        String raw = redis.opsForValue().get(TOKEN_PREFIX + token);
        if (raw == null) return Optional.empty();
        try {
            TokenData data = json.readValue(raw, TokenData.class);
            return data.upload() == upload ? Optional.of(data) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void store(TokenData data, InputStream body, long contentLength) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(data.objectKey())
                        .contentType(data.contentType())
                        .build(),
                RequestBody.fromInputStream(body, contentLength));
    }

    public ResponseInputStream<GetObjectResponse> open(TokenData data) {
        return s3.getObject(GetObjectRequest.builder()
                .bucket(props.bucket()).key(data.objectKey()).build());
    }

    /** One-shot tokens for downloads would break resumable transfers, so tokens are TTL-bound only. */
    private String mint(TokenData data, Duration ttl) {
        byte[] buf = new byte[16];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        try {
            redis.opsForValue().set(TOKEN_PREFIX + token, json.writeValueAsString(data), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Could not mint storage token", e);
        }
        return token;
    }
}