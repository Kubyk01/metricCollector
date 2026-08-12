package com.fixture;

import com.model.Metric;
import com.model.MetricComponent;
import com.model.MetricType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MetricFixture {
    String name = "metric.test";
    String description = "description.test";
    String unit = "unit.test";
    List<String> tags = new ArrayList<>(Arrays.asList("tag1", "tag2"));
    String origin = "origin.test";
    MetricType metricType = MetricType.NUMERICAL;
    List<MetricComponent> components = new ArrayList<>();

    public Metric build() {
        return new Metric(
            name,
            unit,
            origin,
            metricType,
            description,
            tags,
            components
        );
    }

    public MetricFixture withName(String name) {
        this.name = name;
        return this;
    }

    public MetricFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public MetricFixture withUnit(String unit) {
        this.unit = unit;
        return this;
    }

    public MetricFixture withTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public MetricFixture withOrigin(String origin) {
        this.origin = origin;
        return this;
    }

    public MetricFixture withMetricType(MetricType metricType) {
        this.metricType = metricType;
        return this;
    }

    public MetricFixture withComponents(List<MetricComponent> components) {
        this.components = components;
        return this;
    }
}

