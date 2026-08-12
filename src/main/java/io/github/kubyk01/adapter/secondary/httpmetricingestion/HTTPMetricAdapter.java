package io.github.kubyk01.adapter.secondary.httpmetricingestion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.kubyk01.model.Metric;
import io.github.kubyk01.model.MetricComponent;
import io.github.kubyk01.port.secondary.MetricPort;
import io.github.kubyk01.collector.error.HttpErrorHandler;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HTTPMetricAdapter implements MetricPort {

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final String bearerToken;

    private static final String METRICS_PATH = "/metric";
    private static final String METRICS_NAMES_PATH = "/metric/id";
    private static final String COMPONENT_METADATA_PATH_TEMPLATE = "/metric/%s/component";
    private static final String BATCH_COMPONENT_VALUES_PATH = "/metric/component/values/batch";

    public HTTPMetricAdapter() {
        String base = io.github.kubyk01.configuration.EnvVarProvider.getBaseUrl();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        String token = io.github.kubyk01.configuration.EnvVarProvider.getToken();
        this.bearerToken = (token != null && !token.trim().isEmpty()) ? token : null;
    }

    @Override
    public Mono<Void> sendMetric(Metric metric) {
        return sendMetrics(Collections.singletonList(metric));
    }

    @Override
    public Mono<Void> sendMetrics(List<Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return Mono.empty();
        }
        return postRequest(METRICS_PATH, metrics)
            .onErrorResume(e -> {
                HttpErrorHandler.handle(e, metrics);
                return Mono.error(e);
            })
            .then();
    }

    @Override
    public Mono<Void> sendComponentsValues(Map<UUID, List<MetricComponent>> componentsByMetricId) {
        if (componentsByMetricId == null || componentsByMetricId.isEmpty()) {
            return Mono.empty();
        }

        return postRequest(BATCH_COMPONENT_VALUES_PATH, componentsByMetricId)
            .onErrorResume(e -> {
                HttpErrorHandler.handle(e, Collections.emptyList());
                return Mono.error(e);
            })
            .then();
    }

    @Override
    public Mono<Void> sendMetricsComponents(UUID metricId, List<MetricComponent> metricComponents) {
        if (metricComponents == null || metricComponents.isEmpty()) {
            return Mono.empty();
        }
        String path = String.format(COMPONENT_METADATA_PATH_TEMPLATE, metricId);
        return postRequest(path, metricComponents)
            .onErrorResume(e -> {
                HttpErrorHandler.handle(e, Collections.emptyList());
                return Mono.error(e);
            })
            .then();
    }

    @Override
    public Mono<Map<String, String>> retrievalUUIDs(List<String> metricsNames) {
        if (metricsNames == null || metricsNames.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }
        Map<String, List<String>> body = new HashMap<>();
        body.put("names", metricsNames);
        return postRequest(METRICS_NAMES_PATH, body, Map.class)
            .map(response -> {
                Object ids = response.get("metric_ids");
                if (ids instanceof Map) {
                    return (Map<String, String>) ids;
                }
                return Collections.emptyMap();
            });
    }

    private <T> Mono<Void> postRequest(String path, T body) {
        return postRequest(path, body, null).then();
    }

    private <T, R> Mono<R> postRequest(String path, T body, Class<R> responseClass) {
        return Mono.fromCallable(() -> {
            String url = baseUrl + path;
            String json = objectMapper.writeValueAsString(body);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (bearerToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int status = conn.getResponseCode();
            String responseMessage = conn.getResponseMessage();

            if (status >= 200 && status < 300) {
                if (responseClass == null) {
                    return null;
                }
                try (java.io.InputStream is = conn.getInputStream()) {
                    String responseBody = new java.util.Scanner(is, StandardCharsets.UTF_8.name())
                        .useDelimiter("\\A").next();
                    return objectMapper.readValue(responseBody, responseClass);
                }
            } else {
                String statusName = responseMessage != null ? responseMessage : "unknown";
                HttpErrorHandler.handle(status, statusName, Collections.emptyList());
                throw new RuntimeException("HTTP error " + status + ": " + responseMessage);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}