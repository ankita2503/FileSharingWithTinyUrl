package dev.system.tinyurl.Exceptions;

import dev.system.tinyurl.url.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", e);
    }

    @ExceptionHandler(LinkExpiredException.class)
    ProblemDetail expired(LinkExpiredException e) {
        return problem(HttpStatus.GONE, "EXPIRED", e);
    }

    @ExceptionHandler(AliasTakenException.class)
    ProblemDetail aliasTaken(AliasTakenException e) {
        return problem(HttpStatus.CONFLICT, "ALIAS_TAKEN", e);
    }

    @ExceptionHandler(InvalidUrlException.class)
    ProblemDetail invalidUrl(InvalidUrlException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_URL", e);
    }

    @ExceptionHandler(InvalidExpiryException.class)
    ProblemDetail invalidExpiry(InvalidExpiryException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_EXPIRY", e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setProperty("code", "VALIDATION_FAILED");
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String code, RuntimeException e) {
        var pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setProperty("code", code);
        return pd;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail methodValidation(HandlerMethodValidationException e) {
        String detail = e.getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setProperty("code", "VALIDATION_FAILED");
        return pd;
    }

    @ExceptionHandler(FileShareNotFoundException.class)
    ProblemDetail fileNotFound(FileShareNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", e);
    }

    @ExceptionHandler(UploadNotCompletedException.class)
    ProblemDetail uploadNotCompleted(UploadNotCompletedException e) {
        return problem(HttpStatus.CONFLICT, "UPLOAD_NOT_COMPLETED", e);
    }

    @ExceptionHandler(FileTooLargeException.class)
    ProblemDetail tooLarge(FileTooLargeException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", e);
    }

    @ExceptionHandler(InvalidShareRequestException.class)
    ProblemDetail invalidShare(InvalidShareRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_SHARE_REQUEST", e);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                root.getClass().getSimpleName() + ": " + root.getMessage());
        pd.setProperty("code", "MALFORMED_REQUEST");
        return pd;
    }

    public static class FileShareNotFoundException extends RuntimeException {
        public FileShareNotFoundException(String key) {
            super("No file share for key '" + key + "'");
        }
    }

    public static class UploadNotCompletedException extends RuntimeException {
        public UploadNotCompletedException(String key) {
            super("No uploaded object found for share '" + key + "'");
        }
    }

    public static class FileTooLargeException extends RuntimeException {
        public FileTooLargeException(long actual, long max) {
            super("File is " + actual + " bytes, limit is " + max);
        }
    }

    public static class InvalidShareRequestException extends RuntimeException {
        public InvalidShareRequestException(String message) {
            super(message);
        }
    }
}