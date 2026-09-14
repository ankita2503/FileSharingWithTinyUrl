package dev.system.tinyurl.files;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * File links are capability URLs: possession of the URL is the authorization, so the
 * key must be unguessable. Short-link keys are Snowflake-derived and therefore
 * enumerable, which is acceptable for public links but would be a vulnerability here.
 * 128 bits from SecureRandom, URL-safe Base64 without padding = 22 characters.
 */
@Component
public class SecretKeyGenerator {

    private static final int ENTROPY_BYTES = 16;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String generate() {
        byte[] buf = new byte[ENTROPY_BYTES];
        random.nextBytes(buf);
        return encoder.encodeToString(buf);
    }
}