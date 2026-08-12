package io.github.kubyk01.collector.error;

public enum ErrorPolicy {

    /// EXCEPTIONS
    JSON_PROCESSING(false, "json-processing-exception"),
    IO(true, "io-error"),
    INTERRUPTED(true, "interrupted-exception"),
    UNKNOWN(true, "unknown-error"),

    /// HTTP ERRORS
    BAD_REQUEST(false, "bad-request"),
    UNAUTHORIZED(true, "unauthorized"),
    FORBIDDEN(false, "forbidden"),
    NOT_FOUND(false, "not-found"),
    PAYLOAD_TOO_LARGE(true, "payload-too-large"),
    SINGLE_METRIC_TOO_LARGE(false, "single-metric-too-large"),

    // GRPC ERRORS
    CANCELLED(true, "grpc-cancelled"),
    INVALID_ARGUMENT(false, "grpc-invalid-argument"),
    DEADLINE_EXCEEDED(true, "grpc-deadline-exceeded"),
    ALREADY_EXISTS(false, "grpc-already-exists"),
    PERMISSION_DENIED(false, "grpc-permission-denied"),
    RESOURCE_EXHAUSTED(true, "grpc-resource-exhausted"),
    FAILED_PRECONDITION(false, "grpc-failed-precondition"),
    ABORTED(true, "grpc-aborted"),
    OUT_OF_RANGE(false, "grpc-out-of-range"),
    UNIMPLEMENTED(false, "grpc-unimplemented"),
    INTERNAL(true, "grpc-internal"),
    UNAVAILABLE(true, "grpc-unavailable"),
    DATA_LOSS(true, "grpc-data-loss"),
    UNAUTHENTICATED(false, "grpc-unauthenticated");

    private final boolean retryable;
    private final String fileName;

    ErrorPolicy(boolean retryable, String fileName) {
        this.retryable = retryable;
        this.fileName = fileName;
    }

    public boolean retryable() {
        return retryable;
    }

    public String fileName() {
        return fileName;
    }

    public static ErrorPolicy of(String name) {
        if (name == null) return UNKNOWN;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
