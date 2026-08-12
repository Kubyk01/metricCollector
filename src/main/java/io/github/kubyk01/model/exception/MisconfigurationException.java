package io.github.kubyk01.model.exception;

public class MisconfigurationException extends RuntimeException {
    public MisconfigurationException(String message) {
        super("Incorrect MetricCollectorLibrary configuration: " + message);
    }
}