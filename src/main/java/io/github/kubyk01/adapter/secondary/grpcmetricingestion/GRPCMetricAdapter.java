package io.github.kubyk01.adapter.secondary.grpcmetricingestion;

import io.github.kubyk01.collector.error.GrpcErrorHandler;
import io.github.kubyk01.metrichub.AddBatchComponentsRequest;
import io.github.kubyk01.metrichub.AddComponentValuesRequest;
import io.github.kubyk01.metrichub.AddMetricComponentsRequest;
import io.github.kubyk01.metrichub.ComponentStubDTO;
import io.github.kubyk01.metrichub.GetMetricIdsByNameRequest;
import io.github.kubyk01.metrichub.GetMetricIdsByNameResponse;
import io.github.kubyk01.metrichub.MetricComponentDTO;
import io.github.kubyk01.metrichub.MetricDTO;
import io.github.kubyk01.metrichub.MetricRequest;
import io.github.kubyk01.metrichub.ProtoMetricComponentOperationType;
import io.github.kubyk01.metrichub.ProtoMetricType;
import io.github.kubyk01.metrichub.ReactorMetricComponentServiceGrpc;
import io.github.kubyk01.metrichub.ReactorMetricServiceGrpc;
import io.github.kubyk01.model.Metric;
import io.github.kubyk01.model.MetricComponent;
import io.github.kubyk01.port.secondary.MetricPort;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GRPCMetricAdapter implements MetricPort, Disposable {

    private final Channel channel;
    private final ManagedChannel managedChannel;
    private final ReactorMetricServiceGrpc.ReactorMetricServiceStub metricStub;
    private final ReactorMetricComponentServiceGrpc.ReactorMetricComponentServiceStub componentStub;

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
        Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    public GRPCMetricAdapter() {
        String baseUrl = io.github.kubyk01.configuration.EnvVarProvider.getBaseUrl();
        String target = baseUrl.replaceFirst("^https?://", "");

        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target);
        boolean usePlaintext = io.github.kubyk01.configuration.EnvVarProvider.getGrpcUsePlaintext();

        if (usePlaintext) {
            builder.usePlaintext();
        }
        ManagedChannel channelWithoutAuth = builder.build();
        this.managedChannel = channelWithoutAuth;

        String token = io.github.kubyk01.configuration.EnvVarProvider.getToken();
        if (token != null && !token.trim().isEmpty()) {
            this.channel = ClientInterceptors.intercept(channelWithoutAuth, createAuthInterceptor(token));
        } else {
            this.channel = channelWithoutAuth;
        }

        this.metricStub = ReactorMetricServiceGrpc.newReactorStub(this.channel);
        this.componentStub = ReactorMetricComponentServiceGrpc.newReactorStub(this.channel);
    }

    private ClientInterceptor createAuthInterceptor(String token) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
                return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(call) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        headers.put(AUTHORIZATION_KEY, "Bearer " + token);
                        super.start(responseListener, headers);
                    }
                };
            }
        };
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

    private ProtoMetricType protoType(io.github.kubyk01.model.MetricType type) {
        switch (type) {
            case NUMERICAL: return ProtoMetricType.NUMERICAL;
            case BOOLEAN: return ProtoMetricType.BOOLEAN;
            case STATE: return ProtoMetricType.STATE;
            default: return ProtoMetricType.NUMERICAL;
        }
    }

    private ProtoMetricComponentOperationType protoOperation(io.github.kubyk01.model.MetricComponentOperationType op) {
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
        managedChannel.shutdown();
    }

    @Override
    public boolean isDisposed() {
        return managedChannel.isShutdown();
    }
}