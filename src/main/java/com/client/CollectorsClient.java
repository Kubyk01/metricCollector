package com.client;

import com.client.error.ExceptionHandler;
import com.client.error.HttpErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.model.Metric;
import reactor.core.publisher.Mono;

import java.util.List;

public class CollectorsClient {
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private final ExceptionHandler exceptionHandler = new ExceptionHandler();
    private final HttpErrorHandler httpErrorHandler = new HttpErrorHandler();

    // TODO: implement retry logic
    public Mono<Void> sendMetricsRetry(List<Metric> metrics) {
        return Mono.empty();
    }


}
