package com.deserializer;

import com.configuration.EnvVarProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.model.Metric;
import com.port.secondary.MetricPort;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MetricStubRunner {
    private final MetricDeserializer metricDeserializer = new MetricDeserializer();
    public static MetricIdCache metricIdCache = new MetricIdCache();
    public final MetricPort metricPort;

    public MetricStubRunner(MetricPort metricPort) {
        this.metricPort = metricPort;
    }

    public Mono<MetricIdCache> loadAndRegisterMetrics() {
        String location = EnvVarProvider.getMetricsConfigLocation();

        List<JsonNode> configs = metricDeserializer.loadFiles(location);

        Map<String, JsonNode> metricsNodesByName = new HashMap<>();
        for (JsonNode config : configs) {
            for (JsonNode metricNode : config.get("metrics")) {
                metricsNodesByName.put(metricNode.get("name").asText(), metricNode);
            }
        }

        List<String> allMetricNames = new ArrayList<>(metricsNodesByName.keySet());
        if (allMetricNames.isEmpty()) {
            return Mono.just(metricIdCache);
        }

        return metricPort.retrievalUUIDs(allMetricNames)
            .flatMap(existingIdsMap -> {
                for (Map.Entry<String, String> entry : existingIdsMap.entrySet()) {
                    metricIdCache.putMetric(entry.getKey(), UUID.fromString(entry.getValue()));
                }

                List<String> missingNames = allMetricNames.stream()
                    .filter(name -> !existingIdsMap.containsKey(name))
                    .collect(Collectors.toList());

                if (missingNames.isEmpty()) {
                    return Mono.just(metricIdCache);
                }

                List<Metric> missingMetrics = missingNames.stream()
                    .map(name -> metricDeserializer.deserializeMetric(metricsNodesByName.get(name)))
                    .collect(Collectors.toList());

                return metricPort.sendMetrics(missingMetrics)
                    .then(Mono.delay(Duration.ofMinutes(1)))
                    .then(metricPort.retrievalUUIDs(allMetricNames))
                    .doOnNext(updatedMap -> {
                        for (Map.Entry<String, String> entry : updatedMap.entrySet()) {
                            metricIdCache.putMetric(entry.getKey(), UUID.fromString(entry.getValue()));
                        }
                    })
                    .thenReturn(metricIdCache);
            });
    }

}
