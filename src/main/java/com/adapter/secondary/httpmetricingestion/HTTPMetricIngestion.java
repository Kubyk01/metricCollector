package com.adapter.secondary.httpmetricingestion;

import com.client.CollectorsClient;
import com.model.Metric;
import com.port.secondary.MetricIngestionPort;
import reactor.core.publisher.Mono;

import java.util.List;

public class HTTPMetricIngestion implements MetricIngestionPort, reactor.core.Disposable {

    private final CollectorsClient client = new CollectorsClient();

    @Override
    public Mono<Void> submitMetric(Metric metric) {
        // TODO: use client for HTTP POST
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendMetricsImmediately(List<Metric> metrics) {
        // TODO: TODO: use client for batch
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
