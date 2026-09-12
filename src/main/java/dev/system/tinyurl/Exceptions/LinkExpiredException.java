package dev.system.tinyurl.Exceptions;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException(String shortKey) { super("Link '" + shortKey + "' has expired"); }
}
