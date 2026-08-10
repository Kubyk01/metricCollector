package com.adapter.secondary.grpcmetricingestion;

import com.example.metrichub.adapter.driving.grpc.AddBatchComponentsRequest;
import com.example.metrichub.adapter.driving.grpc.AddComponentValuesRequest;
import com.example.metrichub.adapter.driving.grpc.AddMetricComponentsRequest;
import com.example.metrichub.adapter.driving.grpc.GetMetricIdsByNameRequest;
import com.example.metrichub.adapter.driving.grpc.GetMetricIdsByNameResponse;
import com.example.metrichub.adapter.driving.grpc.MetricComponentDTO;
import com.example.metrichub.adapter.driving.grpc.MetricDTO;
import com.example.metrichub.adapter.driving.grpc.MetricRequest;
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
        return metricStub.ingestMetric(request).then();
    }

    @Override
    public Mono<Void> sendMetrics(List<Metric> metrics) {
        MetricRequest request = MetricRequest.newBuilder()
            .addAllMetric(metrics.stream()
                .map(this::toProto)
                .collect(Collectors.toList()))
            .build();
        return metricStub.ingestMetric(request).then();
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

        return componentStub.addComponentValues(request).then();
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
        return componentStub.addMetricComponents(request).then();
    }
    @Override
    public Mono<Map<String, String>> retrievalUUIDs(List<String> metricsNames) {
        GetMetricIdsByNameRequest request = GetMetricIdsByNameRequest.newBuilder()
            .addAllNames(metricsNames)
            .build();

        return metricStub.getMetricIdsByName(request)
            .map(GetMetricIdsByNameResponse::getMetricIdsMap);
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
        return builder.build();
    }

    private com.example.metrichub.adapter.driving.grpc.ComponentStubDTO toProtoComponentStub(MetricComponent comp) {
        return com.example.metrichub.adapter.driving.grpc.ComponentStubDTO.newBuilder()
            .setComponentName(comp.getName())
            .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(comp.getTimestamp().toEpochSecond())
                .setNanos(comp.getTimestamp().getNano()))
            .setValue(comp.getValue() != null ? comp.getValue() : "")
            .build();
    }

    private ProtoMetricType protoType(com.model.MetricType type) {
        switch (type) {
            case NUMERICAL: return ProtoMetricType.NUMERICAL;
            case BOOLEAN: return ProtoMetricType.BOOLEAN;
            case STATE: return ProtoMetricType.STATE;
            default: return ProtoMetricType.NUMERICAL;
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