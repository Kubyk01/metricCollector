package com.adapter.secondary.httpmetricingestion;

import com.model.Metric;
import com.port.secondary.MetricIngestionPort;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.List;

public class MetricIngestion implements MetricIngestionPort, Disposable {

    @Override
    public Mono<Void> submitMetric(Metric metric) {
        return null;
    }

    @Override
    public Mono<Void> sendMetricsImmediately(List<Metric> metrics) {
        return null;
    }

    @Override
    public void dispose() {

    }

    @Override
    public boolean isDisposed() {
        return Disposable.super.isDisposed();
    }
}
