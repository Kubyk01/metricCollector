package io.github.kubyk01.fixture;

import io.github.kubyk01.model.MetricComponent;
import io.github.kubyk01.model.MetricComponentOperationType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MetricComponentFixture {
    String name = "componentName.test";
    String key = "key.test";
    List<String> tags = new ArrayList<>(Arrays.asList("tag1", "tag2"));
    Long evaluationOrder = 1L;
    MetricComponentOperationType operation = MetricComponentOperationType.NUMERICAL_ADD;
    ZonedDateTime timestamp = Instant.now().atZone(ZoneId.of("UTC"));
    String value = "1";

    public MetricComponent build() {
        return new MetricComponent(
            name,
            timestamp,
            key,
            value,
            operation,
            tags,
            evaluationOrder
        );
    }

    public MetricComponentFixture withName(String name) {
        this.name = name;
        return this;
    }

    public MetricComponentFixture withKey(String key) {
        this.key = key;
        return this;
    }

    public MetricComponentFixture withTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public MetricComponentFixture withEvaluationOrder(Long evaluationOrder) {
        this.evaluationOrder = evaluationOrder;
        return this;
    }

    public MetricComponentFixture withOperation(MetricComponentOperationType operation) {
        this.operation = operation;
        return this;
    }

    public MetricComponentFixture withTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public MetricComponentFixture withValue(String value) {
        this.value = value;
        return this;
    }

}

