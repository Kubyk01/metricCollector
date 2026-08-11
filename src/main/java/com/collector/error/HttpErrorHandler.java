package com.collector.error;

import com.model.Metric;
import com.storage.FailedBatchStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public final class HttpErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpErrorHandler.class);

    private HttpErrorHandler() {}

    public static ErrorPolicy resolve(Throwable throwable) {
        if (throwable instanceof IOException) {
            return ErrorPolicy.IO;
        }
        if (throwable instanceof InterruptedException) {
            return ErrorPolicy.INTERRUPTED;
        }
        return ErrorPolicy.UNKNOWN;
    }

    public static ErrorPolicy resolveByStatusCode(int statusCode, String statusName) {
        ErrorPolicy byName = ErrorPolicy.of(statusName);
        if (byName != ErrorPolicy.UNKNOWN) {
            return byName;
        }
        if (statusCode >= 500 && statusCode < 600) {
            return ErrorPolicy.UNKNOWN;
        } else if (statusCode >= 400 && statusCode < 500) {
            return ErrorPolicy.BAD_REQUEST;
        }
        return ErrorPolicy.UNKNOWN;
    }

    public static void handle(Throwable throwable, List<Metric> metrics) {
        ErrorPolicy policy = resolve(throwable);
        FailedBatchStorage.save(metrics, policy.fileName());
        LOGGER.error("HTTP error [{}] while sending {} metrics: {}",
            policy.name(), metrics.size(), throwable.getMessage());
    }

    public static void handle(int statusCode, String statusName, List<Metric> metrics) {
        ErrorPolicy policy = resolveByStatusCode(statusCode, statusName);
        FailedBatchStorage.save(metrics, policy.fileName());
        LOGGER.error("HTTP error [{}] (status {}) while sending {} metrics",
            policy.name(), statusCode, metrics.size());
    }
}