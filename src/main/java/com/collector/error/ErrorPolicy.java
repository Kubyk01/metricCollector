package com.collector.error;

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
    DEFAULT_ERROR(true, "mcl-error");

    // GRPC ERRORS


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
}
