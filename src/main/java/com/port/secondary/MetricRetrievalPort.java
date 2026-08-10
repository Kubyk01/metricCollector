package com.port.secondary;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MetricRetrievalPort {

    Mono<Map<String, String>> retrievalUUIDs(List<String> metricsNames);
}
