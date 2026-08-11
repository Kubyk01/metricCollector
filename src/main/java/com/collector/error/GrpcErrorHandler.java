package com.collector.error;

import com.model.Metric;
import com.storage.FailedBatchStorage;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class GrpcErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcErrorHandler.class);

    private GrpcErrorHandler() {}

    public static ErrorPolicy resolve(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException sre = (StatusRuntimeException) throwable;
            return ErrorPolicy.of(sre.getStatus().getCode().name());
        }
        return ErrorPolicy.UNKNOWN;
    }

    public static void handle(Throwable throwable, List<Metric> metrics) {
        ErrorPolicy policy = resolve(throwable);
        FailedBatchStorage.save(metrics, policy.fileName());
        LOGGER.error("gRPC error [{}] while sending {} metrics: {}",
            policy.name(), metrics.size(), throwable.getMessage());

    }
}