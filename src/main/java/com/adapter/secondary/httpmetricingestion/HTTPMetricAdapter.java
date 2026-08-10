package com.adapter.secondary.httpmetricingestion;

import com.model.Metric;
import com.model.MetricComponent;
import com.port.secondary.MetricIngestionPort;
import com.port.secondary.MetricRetrievalPort;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HTTPMetricAdapter implements MetricIngestionPort, MetricRetrievalPort, Disposable {

    @Override
    public Mono<Void> sendMetric(Metric metric) {
        // TODO: use client for HTTP POST
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendMetrics(List<Metric> metrics) {
        // TODO: TODO: use client for batch
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendComponentsValues(UUID metricId, MetricComponent metricComponent) {
        return null;
    }

    @Override
    public Mono<Void> sendMetricsComponents(UUID metricId, List<MetricComponent> metricComponents) {
        return null;
    }

    @Override
    public Mono<Map<String, String>> retrievalUUIDs(List<String> metricsNames) {
        return null;
    }

    @Override
    public void dispose() {

    }

    @Override
    public boolean isDisposed() {
        return false;
    }
}
