package dev.system.tinyurl.Exceptions;

public class AliasTakenException extends RuntimeException {
    public AliasTakenException(String alias) { super("Alias '" + alias + "' is already taken"); }
}
