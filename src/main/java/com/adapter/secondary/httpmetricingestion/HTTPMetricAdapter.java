package com.adapter.secondary.httpmetricingestion;

import com.model.Metric;
import com.model.MetricComponent;
import com.port.secondary.MetricPort;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HTTPMetricAdapter implements MetricPort, Disposable {

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
    public Mono<Void> sendComponentsValues(Map<UUID, List<MetricComponent>> componentsByMetricId) {
        // TODO: реализовать отправку компонентов через HTTP (например, с группировкой по метрикам)
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendMetricsComponents(UUID metricId, List<MetricComponent> metricComponents) {
        return MetricPort.super.sendMetricsComponents(metricId, metricComponents);
    }

    @Override
    public Mono<Map<String, String>> retrievalUUIDs(List<String> metricsNames) {
        // TODO: реализовать получение UUID
        return Mono.empty();
    }

    @Override
    public void dispose() {

    }

    @Override
    public boolean isDisposed() {
        return false;
    }
}
