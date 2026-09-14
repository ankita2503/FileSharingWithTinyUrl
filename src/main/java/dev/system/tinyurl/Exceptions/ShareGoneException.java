package dev.system.tinyurl.Exceptions;

/**
 * The share existed but is no longer available: expired, download limit reached,
 * burned after reading, or revoked by the sender.
 *
 * Distinct from FileShareNotFoundException on purpose. 410 Gone tells the recipient
 * "this was a real link, you're just too late", while 404 means "this never existed".
 * That distinction is useful to a human and harmless to security here, since the key
 * is 128 bits of entropy — an attacker can't enumerate keys to learn which ones existed.
 */
public class ShareGoneException extends RuntimeException {
    public ShareGoneException(String secretKey) {
        super("Share '" + secretKey + "' is no longer available");
    }
}
