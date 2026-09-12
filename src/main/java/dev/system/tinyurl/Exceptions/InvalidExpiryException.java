package dev.system.tinyurl.Exceptions;

public class InvalidExpiryException extends RuntimeException {
    public InvalidExpiryException() { super("expiresAt must be in the future"); }
}
