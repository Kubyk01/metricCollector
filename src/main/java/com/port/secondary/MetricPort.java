package com.port.secondary;

import com.model.Metric;
import com.model.MetricComponent;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MetricPort {

    Mono<Void> sendMetric(Metric metric);

    Mono<Void> sendMetrics(List<Metric> metrics);

    Mono<Void> sendComponentsValues(Map<UUID, List<MetricComponent>> componentsByMetricId);

    default Mono<Void> sendMetricsComponents(UUID metricId, List<MetricComponent> metricComponents) {
        return sendComponentsValues(Collections.singletonMap(metricId, metricComponents));
    }

    Mono<Map<String, String>> retrievalUUIDs(List<String> metricsNames);

}
