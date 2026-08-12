package io.github.kubyk01.deserializer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MetricIdCache {
    protected final Map<String, UUID> metricNameToId = new ConcurrentHashMap<>();

    public void putMetric(String name, UUID id) {
        metricNameToId.put(name, id);
    }

    public UUID getMetricIdByMetricName(String metricName) {
        return metricNameToId.get(metricName);
    }

}
