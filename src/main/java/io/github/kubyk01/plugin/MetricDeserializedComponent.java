package io.github.kubyk01.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricDeserializedComponent {
    private String name;
    private String operation;
    private String timestamp;
    private String key;
    private String trigger;
    private String operation_trigger;
    private String value;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    public String getOperation_trigger() { return operation_trigger; }
    public void setOperation_trigger(String operation_trigger) { this.operation_trigger = operation_trigger; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MetricConfig {
        public List<MetricDef> metrics;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MetricDef {
        public String name;
        public String unit;
        public String origin;
        public String type;
        public String description;
        public List<String> tags;
        public List<MetricDeserializedComponent> components;
    }
}
