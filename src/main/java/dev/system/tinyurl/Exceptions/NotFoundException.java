package dev.system.tinyurl.Exceptions;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String shortKey) { super("No link for key '" + shortKey + "'"); }
}

