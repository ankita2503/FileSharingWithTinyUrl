package dev.system.tinyurl.utility;

import dev.system.tinyurl.Exceptions.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlNormalizer {

    private static final Set<String> SCHEMES = Set.of("http", "https");
    private static final int MAX_LENGTH = 2048;

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidUrlException("url is required");
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) throw new InvalidUrlException("url exceeds " + MAX_LENGTH + " chars");

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("malformed url");
        }

        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme == null || !SCHEMES.contains(scheme)) throw new InvalidUrlException("only http/https allowed");
        if (uri.getHost() == null) throw new InvalidUrlException("url must have a host");

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || (scheme.equals("http") && port == 80)
                || (scheme.equals("https") && port == 443);

        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) ? "/" : uri.getRawPath();

        StringBuilder sb = new StringBuilder(scheme).append("://");
        if (uri.getRawUserInfo() != null) sb.append(uri.getRawUserInfo()).append('@');
        sb.append(host);
        if (!defaultPort) sb.append(':').append(port);
        sb.append(path);
        if (uri.getRawQuery() != null) sb.append('?').append(uri.getRawQuery());
        // fragment intentionally dropped: never sent to the server
        return sb.toString();
    }
}