package com.model;

import java.util.List;

public class Metric {
    private String name;
    private String unit;
    private String origin;
    private MetricType type;
    private String description;
    private List<String> tags;
    private List<MetricComponent> components;

    protected Metric() {}

    public Metric(String name, String unit, String origin, MetricType type) {
        this(name, unit, origin, type, null, null, null);
    }

    public Metric(String name, String unit, String origin, MetricType type, String description) {
        this(name, unit, origin, type, description, null, null);
    }

    public Metric(String name, String unit, String origin, MetricType type, List<String> tags, String description) {
        this(name, unit, origin, type, description, tags, null);
    }

    public Metric(String name, String unit, String origin, MetricType type, List<MetricComponent> components) {
        this(name, unit, origin, type, null, null, components);
    }

    public Metric(String name, String unit, String origin, MetricType type, String description, List<MetricComponent> components) {
        this(name, unit, origin, type, description, null, components);
    }

    public Metric(String name, String unit, String origin, MetricType type, List<String> tags, List<MetricComponent> components) {
        this(name, unit, origin, type, null, tags, components);
    }

    public Metric(String name, String unit, String origin, MetricType type, String description, List<String> tags, List<MetricComponent> components) {
        this.name = name;
        this.unit = unit;
        this.origin = origin;
        this.type = type;
        this.description = description;
        this.tags = tags;
        this.components = components;
    }

    public Metric setName(String name) {
        this.name = name;
        return this;
    }

    public Metric setDescription(String description) {
        this.description = description;
        return this;
    }

    public Metric setUnit(String unit) {
        this.unit = unit;
        return this;
    }

    public Metric setOrigin(String origin) {
        this.origin = origin;
        return this;
    }

    public Metric setType(MetricType type) {
        this.type = type;
        return this;
    }

    public Metric setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public Metric setComponents(List<MetricComponent> components) {
        this.components = components;
        return this;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public String getOrigin() {
        return origin;
    }

    public MetricType getType() {
        return type;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<MetricComponent> getComponents() {
        return components;
    }
}

