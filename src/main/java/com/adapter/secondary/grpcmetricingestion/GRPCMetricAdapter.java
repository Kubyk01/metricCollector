package com.adapter.secondary.grpcmetricingestion;

import com.collector.error.GrpcErrorHandler;
import com.example.metrichub.adapter.driving.grpc.AddBatchComponentsRequest;
import com.example.metrichub.adapter.driving.grpc.AddComponentValuesRequest;
import com.example.metrichub.adapter.driving.grpc.AddMetricComponentsRequest;
import com.example.metrichub.adapter.driving.grpc.ComponentStubDTO;
import com.example.metrichub.adapter.driving.grpc.GetMetricIdsByNameRequest;
import com.example.metrichub.adapter.driving.grpc.GetMetricIdsByNameResponse;
import com.example.metrichub.adapter.driving.grpc.MetricComponentDTO;
import com.example.metrichub.adapter.driving.grpc.MetricDTO;
import com.example.metrichub.adapter.driving.grpc.MetricRequest;
import com.example.metrichub.adapter.driving.grpc.ProtoMetricComponentOperationType;
import com.example.metrichub.adapter.driving.grpc.ProtoMetricType;
import com.example.metrichub.adapter.driving.grpc.ReactorMetricComponentServiceGrpc;
import com.example.metrichub.adapter.driving.grpc.ReactorMetricServiceGrpc;
import com.model.Metric;
import com.model.MetricComponent;
import com.port.secondary.MetricPort;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GRPCMetricAdapter implements MetricPort, Disposable {

    private final ManagedChannel channel;
    private final ReactorMetricServiceGrpc.ReactorMetricServiceStub metricStub;
    private final ReactorMetricComponentServiceGrpc.ReactorMetricComponentServiceStub componentStub;

    public GRPCMetricAdapter() {
        String baseUrl = com.configuration.EnvVarProvider.getBaseUrl();
        String target = baseUrl.replaceFirst("^https?://", "");
        this.channel = ManagedChannelBuilder.forTarget(target)
            // todo make in env ability to use plaintext or TLS/SSL
            .usePlaintext()
            .build();
        this.metricStub = ReactorMetricServiceGrpc.newReactorStub(channel);
        this.componentStub = ReactorMetricComponentServiceGrpc.newReactorStub(channel);
    }

    @Override
    public Mono<Void> sendMetric(Metric metric) {
        MetricRequest request = MetricRequest.newBuilder()
            .addMetric(toProto(metric))
            .build();
        return metricStub.ingestMetric(request)
            .doOnError(e -> GrpcErrorHandler.handle(e, Collections.singletonList(metric)))
            .then();
    }

    @Override
    public Mono<Void> sendMetrics(List<Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return Mono.empty();
        }
        MetricRequest request = MetricRequest.newBuilder()
            .addAllMetric(metrics.stream()
                .map(this::toProto)
                .collect(Collectors.toList()))
            .build();
        return metricStub.ingestMetric(request)
            .doOnError(e -> GrpcErrorHandler.handle(e, metrics))
            .then();
    }

    @Override
    public Mono<Void> sendComponentsValues(Map<UUID, List<MetricComponent>> componentsByMetricId) {
        if (componentsByMetricId == null || componentsByMetricId.isEmpty()) {
            return Mono.empty();
        }

        AddBatchComponentsRequest.Builder batchBuilder = AddBatchComponentsRequest.newBuilder();

        for (Map.Entry<UUID, List<MetricComponent>> entry : componentsByMetricId.entrySet()) {
            UUID metricId = entry.getKey();
            List<MetricComponent> comps = entry.getValue();
            if (comps == null || comps.isEmpty()) {
                continue;
            }

            AddComponentValuesRequest.Builder entryBuilder = AddComponentValuesRequest.newBuilder()
                .setMetricId(metricId.toString());

            for (MetricComponent comp : comps) {
                entryBuilder.addComponentStub(toProtoComponentStub(comp));
            }

            batchBuilder.addEntries(entryBuilder.build());
        }

        AddBatchComponentsRequest request = batchBuilder.build();
        if (request.getEntriesCount() == 0) {
            return Mono.empty();
        }

        return componentStub.addComponentValues(request)
            .doOnError(e -> GrpcErrorHandler.handle(e, Collections.emptyList()))
            .then();
    }

    @Override
    public Mono<Void> sendMetricsComponents(UUID metricId, List<MetricComponent> metricComponents) {
        if (metricComponents == null || metricComponents.isEmpty()) {
            return Mono.empty();
        }
        AddMetricComponentsRequest request = AddMetricComponentsRequest.newBuilder()
            .setMetricId(metricId.toString())
            .addAllComponents(metricComponents.stream()
                .map(this::toProtoComponent)
                .collect(Collectors.toList()))
            .build();
        return componentStub.addMetricComponents(request)
            .doOnError(e -> GrpcErrorHandler.handle(e, Collections.emptyList()))
            .then();
    }

    @Override
    public Mono<Map<String, String>> retrievalUUIDs(List<String> metricsNames) {
        if (metricsNames == null || metricsNames.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        GetMetricIdsByNameRequest request = GetMetricIdsByNameRequest.newBuilder()
            .addAllNames(metricsNames)
            .build();

        return metricStub.getMetricIdsByName(request)
            .map(GetMetricIdsByNameResponse::getMetricIdsMap)
            .doOnError(e -> GrpcErrorHandler.handle(e, Collections.emptyList()));
    }

    private MetricDTO toProto(Metric metric) {
        MetricDTO.Builder builder = MetricDTO.newBuilder()
            .setName(metric.getName())
            .setUnit(metric.getUnit())
            .setOrigin(metric.getOrigin())
            .setType(protoType(metric.getType()));
        if (metric.getDescription() != null) {
            builder.setDescription(metric.getDescription());
        }
        if (metric.getTags() != null) {
            builder.addAllTags(metric.getTags());
        }
        if (metric.getComponents() != null) {
            builder.addAllComponents(metric.getComponents().stream()
                .map(this::toProtoComponent)
                .collect(Collectors.toList()));
        }
        return builder.build();
    }

    private MetricComponentDTO toProtoComponent(MetricComponent comp) {
        MetricComponentDTO.Builder builder =
            MetricComponentDTO.newBuilder()
                .setName(comp.getName())
                .setKey(comp.getKey() != null ? comp.getKey() : "")
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(comp.getTimestamp().toEpochSecond())
                    .setNanos(comp.getTimestamp().getNano()))
                .setValue(comp.getValue() != null ? comp.getValue() : "");
        if (comp.getTags() != null) {
            builder.addAllTags(comp.getTags());
        }
        if (comp.getOperation() != null) {
            builder.setOperation(protoOperation(comp.getOperation()));
        }
        if (comp.getEvaluationOrder() != null) {
            builder.setEvaluationOrder(comp.getEvaluationOrder());
        }
        return builder.build();
    }

    private ComponentStubDTO toProtoComponentStub(MetricComponent comp) {
       ComponentStubDTO.Builder builder =
            ComponentStubDTO.newBuilder()
                .setComponentName(comp.getName())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(comp.getTimestamp().toEpochSecond())
                    .setNanos(comp.getTimestamp().getNano()));
        if (comp.getValue() != null) {
            builder.setValue(comp.getValue());
        }
        return builder.build();
    }

    private ProtoMetricType protoType(com.model.MetricType type) {
        switch (type) {
            case NUMERICAL: return ProtoMetricType.NUMERICAL;
            case BOOLEAN: return ProtoMetricType.BOOLEAN;
            case STATE: return ProtoMetricType.STATE;
            default: return ProtoMetricType.NUMERICAL;
        }
    }

    private ProtoMetricComponentOperationType protoOperation(com.model.MetricComponentOperationType op) {
        switch (op) {
            case NUMERICAL_ADD: return ProtoMetricComponentOperationType.NUMERICAL_ADD;
            case NUMERICAL_SUBTRACT: return ProtoMetricComponentOperationType.NUMERICAL_SUBTRACT;
            case NUMERICAL_MULTIPLY: return ProtoMetricComponentOperationType.NUMERICAL_MULTIPLY;
            case NUMERICAL_DIVIDE: return ProtoMetricComponentOperationType.NUMERICAL_DIVIDE;
            case NUMERICAL_AVERAGE: return ProtoMetricComponentOperationType.NUMERICAL_AVERAGE;
            case BOOLEAN_AND: return ProtoMetricComponentOperationType.BOOLEAN_AND;
            case BOOLEAN_OR: return ProtoMetricComponentOperationType.BOOLEAN_OR;
            case STATE: return ProtoMetricComponentOperationType.STATE_ALLOW;
            default: return ProtoMetricComponentOperationType.NUMERICAL_ADD;
        }
    }

    @Override
    public void dispose() {
        channel.shutdown();
    }

    @Override
    public boolean isDisposed() {
        return channel.isShutdown();
    }
}