package io.github.kubyk01.collector.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.kubyk01.model.Metric;
import io.github.kubyk01.storage.FailedBatchStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandler.class);

    public void handle(Exception e, List<Metric> metrics, boolean isRetry) {
        ErrorPolicy policy = resolvePolicy(e);

        if (policy.equals(ErrorPolicy.INTERRUPTED)) {
            Thread.currentThread().interrupt();
        }

        if (!isRetry) {
            FailedBatchStorage.save(metrics, policy.fileName());

            if (!policy.retryable()) {
                log(e, metrics.size(), policy);
            }

            return;
        }

        log(e, metrics.size(), policy);
        throw new RuntimeException(e);
    }

    private ErrorPolicy resolvePolicy(Exception e) {
        if (e instanceof JsonProcessingException) {
            return ErrorPolicy.JSON_PROCESSING;
        }
        if (e instanceof IOException) {
            return ErrorPolicy.IO;
        }
        if (e instanceof InterruptedException) {
            return ErrorPolicy.INTERRUPTED;
        }
        return ErrorPolicy.UNKNOWN;
    }

    private void log(Exception e, int metricCount, ErrorPolicy policy) {
        LOGGER.error(
            "Error [{}] while sending {} metrics. message={}, metrics saved to file=/failed-batches/{}_timestamp.json",
            policy.name(),
            metricCount,
            e.getMessage(),
            policy.fileName()
        );
    }
}

