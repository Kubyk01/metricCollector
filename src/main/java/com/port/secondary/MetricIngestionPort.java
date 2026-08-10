package com.port.secondary;

import com.model.Metric;
import com.model.MetricComponent;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface MetricIngestionPort {

    Mono<Void> sendMetric(Metric metric);

    Mono<Void> sendMetrics(List<Metric> metrics);

    Mono<Void> sendComponentsValues(UUID metricId, MetricComponent metricComponent);

    Mono<Void> sendMetricsComponents(UUID metricId, List<MetricComponent> metricComponents);
}
