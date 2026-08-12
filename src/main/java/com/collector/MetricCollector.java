package com.collector;

import com.adapter.secondary.httpmetricingestion.HTTPMetricAdapter;
import com.adapter.secondary.grpcmetricingestion.GRPCMetricAdapter;
import com.configuration.EnvVarProvider;
import com.deserializer.MetricIdCache;
import com.deserializer.MetricStubRunner;
import com.model.Metric;
import com.model.MetricComponent;
import com.port.secondary.MetricPort;
import com.storage.FailedBatchRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MetricCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricCollector.class);
    private static MetricPort metricPort;
    private static Mono<MetricIdCache> cacheMono;

    private static final int BATCH_SIZE = EnvVarProvider.getBatchSize();
    private static Map<UUID, List<MetricComponent>> batchBuffer = new ConcurrentHashMap<>();
    private static AtomicBoolean shutdown = new AtomicBoolean(false);
    private static final Disposable flushDisposable;

    static {
        String protocol = EnvVarProvider.getProtocol();
        if ("http".equalsIgnoreCase(protocol)) {
            metricPort = new HTTPMetricAdapter();
        } else {
            metricPort = new GRPCMetricAdapter();
        }
        LOGGER.info("MetricCollector initialized with protocol: {}", protocol);

        MetricStubRunner runner = new MetricStubRunner(metricPort);
        cacheMono = runner.loadAndRegisterMetrics().cache();
        cacheMono.subscribe(
            cache -> LOGGER.info("Metric cache loaded, size: {}", cache),
            error -> LOGGER.error("Failed to load metric cache", error)
        );

        if (BATCH_SIZE > 0) {
            flushDisposable = Flux.interval(Duration.ofSeconds(5))
                .subscribe(tick -> flushAllBatches());
            LOGGER.info("Batch processing enabled with size={}, flush interval=5s", BATCH_SIZE);
        } else {
            flushDisposable = null;
            LOGGER.info("Batch processing disabled (batch.size <= 0)");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown.set(true);
            if (flushDisposable != null && !flushDisposable.isDisposed()) {
                flushDisposable.dispose();
            }
            flushAllBatches();
        }));

        Duration retryInterval = EnvVarProvider.getRetryInterval();
        if (retryInterval != null && !retryInterval.isNegative() && !retryInterval.isZero()) {
            FailedBatchRetryService retryService = new FailedBatchRetryService();
            retryService.startRetryScheduler(retryInterval);
            LOGGER.info("Retry service started with interval: {}", retryInterval);
        } else {
            LOGGER.info("Retry service disabled (interval <= 0)");
        }
    }

    public static void submit(Metric metric) {
        metricPort.sendMetric(metric);
    }

    public static Mono<Void> submit(UUID metricId, List<MetricComponent> metricComponents) {
        return metricPort.sendMetricsComponents(metricId, metricComponents);
    }

    public static Mono<Void> submit(String metricName, String componentName, Object value) {
        return cacheMono
            .flatMap(cache -> {
                UUID metricId = cache.getMetricIdByMetricName(metricName);
                if (metricId == null) {
                    return Mono.error(new IllegalArgumentException("Metric not found: " + metricName));
                }
                MetricComponent component = new MetricComponent(
                    componentName,
                    ZonedDateTime.now(),
                    value != null ? value.toString() : null
                );

                if (BATCH_SIZE <= 0) {
                    return metricPort.sendComponentsValues(
                        Collections.singletonMap(metricId, Collections.singletonList(component))
                    );
                }

                return Mono.fromRunnable(() -> {
                    List<MetricComponent> list = batchBuffer.computeIfAbsent(
                        metricId,
                        k -> Collections.synchronizedList(new ArrayList<>())
                    );
                    synchronized (list) {
                        list.add(component);
                        if (list.size() >= BATCH_SIZE) {
                            List<MetricComponent> toSend = batchBuffer.remove(metricId);
                            if (toSend != null && !toSend.isEmpty()) {
                                metricPort.sendComponentsValues(
                                    Collections.singletonMap(metricId, toSend)
                                ).subscribe(
                                    null,
                                    err -> LOGGER.error("Failed to send batch for metricId {}: {}", metricId, err.getMessage())
                                );
                            }
                        }
                    }
                });
            });
    }

    private static void flushAllBatches() {
        if (shutdown.get() && batchBuffer.isEmpty()) {
            return;
        }
        Map<UUID, List<MetricComponent>> allRemaining = new HashMap<>(batchBuffer);
        batchBuffer.clear();

        if (!allRemaining.isEmpty()) {
            metricPort.sendComponentsValues(allRemaining)
                .subscribe(
                    null,
                    err -> LOGGER.error("Failed to send remaining batches on flush", err)
                );
        }
    }

    public static Mono<Void> sendMetricsRetry(List<Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return Mono.empty();
        }
        return metricPort.sendMetrics(metrics)
            .doOnError(e -> LOGGER.error("Failed to send retry batch of {} metrics: {}", metrics.size(), e.getMessage()));
    }
}