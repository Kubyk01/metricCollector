package com.deserializer;

import reactor.core.publisher.Mono;

public class MetricStubRunner {

    public static final MetricIdCache metricIdCache = new MetricIdCache();
    private static final MetricLoader metricStubLoader = new MetricLoader(metricIdCache);

    private static final Mono<Void> init = Mono.defer(metricStubLoader::loadAndRegisterMetrics).cache();

    public static Mono<Void> init() {
        return init;
    }

}
