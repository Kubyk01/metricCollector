package com.deserializer;

import com.configuration.EnvVarProvider;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricLoader {
    private final MetricDeserializer metricDeserializer = new MetricDeserializer();

    public Mono<Void> loadAndRegisterMetrics() {
        String location = EnvVarProvider.getMetricsConfigLocation();

        List<JsonNode> configs = metricDeserializer.loadFiles(location);

        Map<String, JsonNode> metricsNodesByName = new HashMap<>();
        for (JsonNode config : configs) {
            for (JsonNode metricNode : config.get("metrics")) {
                metricsNodesByName.put(metricNode.get("name").asText(), metricNode);
            }
        }

        List<String> allMetricNames = new ArrayList<>(metricsNodesByName.keySet());


    }

}
