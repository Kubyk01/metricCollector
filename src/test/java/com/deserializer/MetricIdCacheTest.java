package com.deserializer;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricIdCacheTest {

    @Test
    void getMetricIdByMetricName_shouldReturnId() {
        MetricIdCache metricIdCache = new MetricIdCache();

        UUID metricId = UUID.randomUUID();
        String metricName = "Metric";

        metricIdCache.putMetric(metricName, metricId);

        UUID id = metricIdCache.getMetricIdByMetricName(metricName);

        assertEquals(id, metricId);
    }
}

