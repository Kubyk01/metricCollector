package com.port.secondary;

import com.model.Metric;
import reactor.core.publisher.Mono;

import java.util.List;

public interface MetricIngestionPort {

    Mono<Void> submitMetric(Metric metric);

    Mono<Void> sendMetricsImmediately(List<Metric> metrics);
}
