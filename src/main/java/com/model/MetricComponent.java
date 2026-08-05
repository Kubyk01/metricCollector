package com.model;

import java.time.ZonedDateTime;
import java.util.List;

public class MetricComponent {
    private String name;
    private ZonedDateTime timestamp;
    private String key;
    private String value;
    private MetricComponentOperationType operation;
    private List<String> tags;
    private Long evaluationOrder;

    protected MetricComponent() {}

    public MetricComponent(String name, ZonedDateTime timestamp, String key, String value, MetricComponentOperationType operation) {
        this(name, timestamp, key, value, operation, null, null);
    }

    public MetricComponent(String name, ZonedDateTime timestamp, String key, String value, MetricComponentOperationType operation, List<String> tags) {
        this(name, timestamp, key, value, operation, tags, null);
    }

    public MetricComponent(String name, ZonedDateTime timestamp, String key, String value, MetricComponentOperationType operation, List<String> tags, Long evaluationOrder) {
        this.name = name;
        this.timestamp = timestamp;
        this.key = key;
        this.value = value;
        this.operation = operation;
        this.tags = tags;
        this.evaluationOrder = evaluationOrder;
    }

    public MetricComponent setName(String name) {
        this.name = name;
        return this;
    }

    public MetricComponent setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public MetricComponent setKey(String key) {
        this.key = key;
        return this;
    }

    public MetricComponent setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public MetricComponent setValue(String value) {
        this.value = value;
        return this;
    }

    public MetricComponent setEvaluationOrder(Long evaluationOrder) {
        this.evaluationOrder = evaluationOrder;
        return this;
    }

    public MetricComponent setOperation(MetricComponentOperationType operation) {
        this.operation = operation;
        return this;
    }

    public String getName() {
        return name;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public String getKey() {
        return key;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getValue() {
        return value;
    }

    public Long getEvaluationOrder() {
        return evaluationOrder;
    }

    public MetricComponentOperationType getOperation() {
        return operation;
    }
}