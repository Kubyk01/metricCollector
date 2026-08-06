package com.client;

import com.client.error.ExceptionHandler;
import com.client.error.HttpErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class CollectorsClient {
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    //private final String baseUrl;

    private final ExceptionHandler exceptionHandler = new ExceptionHandler();
    private final HttpErrorHandler httpErrorHandler = new HttpErrorHandler();


}
