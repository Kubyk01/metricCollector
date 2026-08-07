package com.adapter.secondary.grpcmetricingestion;

import com.model.Metric;
import com.model.MetricComponent;
import com.port.secondary.MetricIngestionPort;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

public class GRPCMetricIngestion implements MetricIngestionPort, reactor.core.Disposable {

    private final ManagedChannel channel;
    private final MetricServiceGrpc.MetricServiceStub stub;
    private final MetricServiceGrpc.MetricServiceBlockingStub blockingStub;

    public GRPCMetricIngestion() {
        String baseUrl = com.configuration.EnvVarProvider.getBaseUrl();
        String target = baseUrl.replaceFirst("^https?://", "");
        this.channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        this.stub = MetricServiceGrpc.newStub(channel);
        this.blockingStub = MetricServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public Mono<Void> submitMetric(Metric metric) {
        return Mono.fromRunnable(() -> {
            MetricRequest request = buildRequest(metric);
            blockingStub.ingestMetric(request);
        });
    }

    @Override
    public Mono<Void> sendMetricsImmediately(List<Metric> metrics) {
        return Mono.fromRunnable(() -> {
            MetricRequest request = MetricRequest.newBuilder()
                    .addAllMetric(metrics.stream()
                            .map(this::toProto)
                            .collect(Collectors.toList()))
                    .build();
            blockingStub.ingestMetric(request);
        });
    }

    private MetricRequest buildRequest(Metric metric) {
        return MetricRequest.newBuilder()
                .addMetric(toProto(metric))
                .build();
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

    private com.example.metrichub.adapter.driving.grpc.MetricComponentDTO toProtoComponent(MetricComponent comp) {
        com.example.metrichub.adapter.driving.grpc.MetricComponentDTO.Builder builder =
                com.example.metrichub.adapter.driving.grpc.MetricComponentDTO.newBuilder()
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
