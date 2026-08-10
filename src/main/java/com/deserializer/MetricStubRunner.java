package com.deserializer;

import com.port.secondary.MetricIngestionPort;
import reactor.core.publisher.Mono;

public class MetricStubRunner {

    public static final MetricIdCache metricIdCache = new MetricIdCache();
    public final MetricIngestionPort ingestionPort;
    private static final MetricLoader metricStubLoader = new MetricLoader(metricIdCache);

    private static final Mono<Void> init = Mono.defer(metricStubLoader::loadAndRegisterMetrics).cache();

    public MetricStubRunner(MetricIngestionPort ingestionPort) {
        this.ingestionPort = ingestionPort;
    }

    public static Mono<Void> init() {
        return init;
    }

}
