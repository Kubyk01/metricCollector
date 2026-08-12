# Metric Collector Library & Plugin

A framework-agnostic Java library for collecting and sending metrics with build-time instrumentation support. Designed for reactive, non-blocking metric ingestion via HTTP or gRPC.

## Features

- **Reactive core** – built on Project Reactor for non-blocking metric submission
- **Dual transport** – HTTP and gRPC adapters (configurable per environment)
- **Batch processing** – automatic batching with configurable size and flush interval
- **Retry & persistence** – failed batches are saved to disk and automatically retried
- **Build-time instrumentation** – Maven plugin injects metric collection code into your classes
- **Environment-aware** – configuration per environment (local, dev, test, prod)
- **Metric ID caching** – resolves metric names to UUIDs automatically

## Quick Start

### 1. Add the dependency

```xml
<dependency>
  <groupId>io.github.kubyk01</groupId>
  <artifactId>metric-collector-library</artifactId>
  <version>0.1</version>
</dependency>
```

### 2. Configure the environment

Set the environment variable or system property:

```bash
export metric-collector-library.env=local
```

Or as a JVM property:

```bash
-Dmetric-collector-library.env=local
```

### 3. Define metric configuration

Place JSON configuration files in `src/main/resources/collector-metrics-config/{env}/`:

```json
{
  "metrics": [
    {
      "name": "active-tasks",
      "unit": "task",
      "origin": "my-service",
      "type": "NUMERICAL",
      "components": [
        {
          "name": "active-tasks-daily-count",
          "operation": "NUMERICAL_ADD",
          "timestamp": "dynamic.timestamp",
          "key": "dynamic.key",
          "trigger": "com.example.TaskManager.getActiveCount()",
          "operation_trigger": "increase",
          "value": "com.example.TaskManager.activeCount"
        }
      ]
    }
  ]
}
```

### 4. Use in your code

```java
// Submit a full metric definition
Metric metric = new Metric("my-metric", "count", "my-app", MetricType.NUMERICAL);
MetricCollector.submit(metric);

// Submit a metric component value by name
MetricCollector.submit("active-tasks", "active-tasks-daily-count", 42)
    .subscribe();

// Submit a metric component value by UUID
UUID metricId = ...;
List<MetricComponent> components = ...;
MetricCollector.submit(metricId, components).subscribe();
```

## Build-time Instrumentation

The Maven plugin automatically instruments your compiled classes to capture field changes.

### Add the plugin

```xml
<plugin>
  <groupId>io.github.kubyk01</groupId>
  <artifactId>metric-collector-library</artifactId>
  <version>0.1</version>
  <executions>
    <execution>
      <goals>
        <goal>instrument</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

The plugin reads the same metric configuration files and injects `MetricCollector.submit()` calls when:

- A specified method is invoked (`trigger` points to a method)
- A specified field changes value (`value` points to a field)

### Instrumentation triggers

| Trigger type | Description |
|---|---|
| `increase` | Send metric when field value increases |
| `decrease` | Send metric when field value decreases |
| `changed` (default) | Send metric when field value changes (any difference) |

## Configuration

### metric-collector-library.properties

Place this file in `src/main/resources/` with per-environment settings:

```properties
base.url.local=http://localhost:8080
protocol.local=grpc
batch.size.local=0
retry.interval.local=PT30S
scan.all.classes.local=false
```

### Supported properties

| Property | Description |
|---|---|
| `base.url.{env}` | Backend URL (HTTP or gRPC) |
| `token.url.{env}` | Optional bearer token |
| `protocol.{env}` | `http` or `grpc` |
| `grpc.use.plaintext.{env}` | Use plaintext for gRPC (default: `true`) |
| `batch.size.{env}` | Batch size before flushing (0 = disabled) |
| `retry.interval.{env}` | Retry interval (e.g., `PT30S`, `PT1M`) |
| `scan.all.classes.{env}` | Scan all classes for field instrumentation |
| `metrics-config-location.{env}` | Path to metric config files |

## Protocol support

### HTTP

- JSON payloads over HTTP POST
- Bearer token authentication via `Authorization` header
- Uses Jackson for serialization

### gRPC

- Protocol Buffers over gRPC
- Reactor gRPC stub for reactive streaming
- Bearer token authentication via metadata headers

## Error handling & persistence

- Failed metric batches are saved to the filesystem under `retry-send/` or `failed-batches/`
- Retry service automatically re-sends failed batches at configured intervals
- Permanent failures (e.g., `bad-request`, `json-processing-exception`) are moved to `failed-batches/`
- Files are automatically cleaned up after 3 months

## Batch processing

When `batch.size` is configured > 0:

- Metrics are accumulated in memory
- Flushed automatically every 5 seconds or when batch size is reached
- On application shutdown, remaining batches are flushed

## Requirements

- Java 8+
- Maven 3.6+

## License

Apache License, Version 2.0
