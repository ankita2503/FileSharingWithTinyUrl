package dev.system.tinyurl.Exceptions;

public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message) { super(message); }
}
