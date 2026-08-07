package com.collector;

import com.adapter.secondary.httpmetricingestion.HTTPMetricIngestion;
import com.adapter.secondary.grpcmetricingestion.GRPCMetricIngestion;
import com.configuration.EnvVarProvider;
import com.model.Metric;
import com.model.MetricComponent;
import com.model.MetricComponentOperationType;
import com.model.MetricType;
import com.port.secondary.MetricIngestionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MetricCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricCollector.class);
    private static final MetricIngestionPort ingestionPort;
    private static final Map<String, MetricDefinition> definitions = new ConcurrentHashMap<>();

    static {
        String protocol = EnvVarProvider.getProtocol();
        if ("http".equalsIgnoreCase(protocol)) {
            ingestionPort = new HTTPMetricIngestion();
        } else {
            ingestionPort = new GRPCMetricIngestion();
        }
        LOGGER.info("MetricCollector initialized with protocol: {}",
                com.configuration.EnvVarProvider.getProtocol());
    }

    private MetricCollector() {}

    public static void registerMetric(String name, String unit, String origin, MetricType type,
                                      String description, List<String> tags) {
        definitions.putIfAbsent(name, new MetricDefinition(name, unit, origin, type, description, tags));
    }

    public static void report(String metricName, String componentName, Object value,
                              String key, List<String> tags) {
        if (ingestionPort == null) {
            LOGGER.warn("Ingestion port is null, metric not sent");
            return;
        }
        MetricDefinition def = definitions.get(metricName);
        if (def == null) {
            LOGGER.warn("Metric {} not registered, skipping report", metricName);
            return;
        }
        MetricComponent component = new MetricComponent(
                componentName,
                ZonedDateTime.now(),
                key != null ? key : "default",
                value != null ? value.toString() : null,
                MetricComponentOperationType.STATE,
                tags != null ? tags : Collections.emptyList()
        );
        Metric metric = new Metric(
                def.name,
                def.unit,
                def.origin,
                def.type,
                def.description,
                Collections.singletonList(component)
        );

        ingestionPort.submitMetric(metric).subscribe(
                null,
                err -> LOGGER.error("Failed to send metric {}: {}", metricName, err.getMessage())
        );
    }

    private static class MetricDefinition {
        final String name, unit, origin, description;
        final MetricType type;
        final List<String> tags;
        MetricDefinition(String name, String unit, String origin, MetricType type, String description, List<String> tags) {
            this.name = name;
            this.unit = unit;
            this.origin = origin;
            this.type = type;
            this.description = description;
            this.tags = tags;
        }
    }
}
