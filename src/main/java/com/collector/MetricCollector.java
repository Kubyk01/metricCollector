package com.collector;

import com.adapter.secondary.httpmetricingestion.HTTPMetricAdapter;
import com.adapter.secondary.grpcmetricingestion.GRPCMetricAdapter;
import com.collector.error.ExceptionHandler;
import com.collector.error.GrpcErrorHandler;
import com.configuration.EnvVarProvider;
import com.deserializer.MetricStubRunner;
import com.model.Metric;
import com.model.MetricComponent;
import com.port.secondary.MetricIngestionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public final class MetricCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricCollector.class);
    private static final MetricIngestionPort ingestionPort;


    private final ExceptionHandler exceptionHandler = new ExceptionHandler();
    private final GrpcErrorHandler httpErrorHandler = new GrpcErrorHandler();

    static {
        String protocol = EnvVarProvider.getProtocol();
        if ("http".equalsIgnoreCase(protocol)) {
            ingestionPort = new HTTPMetricAdapter();
        } else {
            ingestionPort = new GRPCMetricAdapter();
        }
        LOGGER.info("MetricCollector initialized with protocol: {}", protocol);

        MetricStubRunner metricStubRunner = new MetricStubRunner(ingestionPort);
        metricStubRunner.
    }

    private MetricCollector() {}

    public static void submit(Metric metric){

    }

    public static Mono<Void> submit(String metricName, String componentName, Object value) {
        if (ingestionPort == null) {
            LOGGER.warn("Ingestion port is null, metric not sent");
            return Mono.error(new IllegalStateException("Ingestion port is null"));
        }

        UUID metricId = UUID_METRIC_NAME_MAP.get(metricName);
        MetricComponent component = new MetricComponent(
                componentName,
                ZonedDateTime.now(),
                value != null ? value.toString() : null
        );

        return ingestionPort.sendComponentsValues(metricId, component);

    }


    public Mono<Void> sendMetricsRetry(List<Metric> metrics) {
        return null;
    }

}
