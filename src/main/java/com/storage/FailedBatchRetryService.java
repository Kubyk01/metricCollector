package com.storage;

import com.collector.MetricCollector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.model.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.stream.Stream;

public class FailedBatchRetryService {
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    private final MetricCollector client;
    private static final Scheduler RETRY_SCHEDULER = Schedulers.newSingle("metric-retry-scheduler");
    private static final Scheduler FILE_SCHEDULER = Schedulers.newBoundedElastic(2, 10000, "metric-file-scheduler");
    public static final int MAX_BATCH_SIZE = 200_000;
    private static final Logger LOGGER = LoggerFactory.getLogger(FailedBatchRetryService.class.getName());

    public FailedBatchRetryService(MetricCollector client) {
        this.client = client;
    }

    public void startRetryScheduler(Duration interval) {
        Flux.interval(interval, RETRY_SCHEDULER)
            .flatMap(tick -> resend())
            .subscribe();
    }

    private Mono<Void> resend() {
        return listRetryFiles()
            .flatMap(this::processRetryFile, 10)
            .then()
            .onErrorResume(e -> Mono.empty());
    }

    private Flux<Path> listRetryFiles() {
        return Flux.using(
                () -> Files.list(FailedBatchStorage.STORAGE_DIR_RETRY),
                stream -> Flux.fromStream(stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().contains("-failed-retry"))
                    .filter(p -> !p.getFileName().toString().contains("-done"))
                ),
                Stream::close
            ).subscribeOn(FILE_SCHEDULER)
            .onErrorResume(e -> Flux.empty());
    }

    private Mono<Void> processRetryFile(Path path) {
        return Mono.fromCallable(() -> objectMapper.readValue(path.toFile(), new TypeReference<List<Metric>>() {}))
            .subscribeOn(FILE_SCHEDULER)
            .flatMapMany(metrics -> {
                if (metrics.isEmpty()) return Flux.empty();

                if (countBatchSize(metrics) <= MAX_BATCH_SIZE) {
                    return Flux.just(metrics);
                }

                return Flux.fromIterable(splitAndPrepareSubBatches(metrics));
            })
            .concatMap(subbatch ->
                client.sendMetricsRetry(subbatch)
                    .onErrorResume(err -> {
                        LOGGER.error(err.getMessage());
                        renameFileToFailed(path);
                        return Mono.empty();
                    })
            )
            .then(Mono.fromRunnable(() -> markFileAsDone(path)))
            .onErrorResume(e -> {
                LOGGER.error(e.getMessage());
                renameFileToFailed(path);
                return Mono.empty();
            }).then();
    }

    public static List<List<Metric>> splitAndPrepareSubBatches(List<Metric> batch) {
        if (batch.size() == 1 && countBatchSize(batch) > MAX_BATCH_SIZE) {
            FailedBatchStorage.save(batch, "single-metric-too-large");
            return new ArrayList<>();
        }

        List<List<Metric>> subbatches = splitBatchBySize(batch);
        ArrayList<List<Metric>> validSubbatches = new ArrayList<>();

        for (List<Metric> subbatch : subbatches) {
            if (countBatchSize(subbatch) > MAX_BATCH_SIZE) {
                FailedBatchStorage.save(subbatch, "sub-batch-too-large");
            } else {
                validSubbatches.add(subbatch);
            }
        }

        return validSubbatches;
    }

    private static List<List<Metric>> splitBatchBySize(List<Metric> metrics) {
        List<List<Metric>> result = new ArrayList<>();
        List<Metric> currentBatch = new ArrayList<>();
        short currentSize = 0;

        for (Metric metric : metrics) {
            int metricSize = countBatchSize(Collections.singletonList(metric));

            if (currentSize + metricSize > MAX_BATCH_SIZE && !currentBatch.isEmpty()) {
                result.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
                currentSize = 0;
            }

            currentBatch.add(metric);
            currentSize += metricSize;
        }

        if (!currentBatch.isEmpty()) {result.add(currentBatch);}
        return result;
    }

    private void renameFileToFailed(Path path) {
        try {
            String name = path.getFileName().toString();
            if (name.contains("-failed-retry")) return;

            if (!Files.exists(FailedBatchStorage.STORAGE_DIR_FAILED)) {
                Files.createDirectories(FailedBatchStorage.STORAGE_DIR_FAILED);
            }

            Path target = FailedBatchStorage.STORAGE_DIR_FAILED.resolve(
                name.replace(".json", "") + "-failed-retry.json"
            );

            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignore) {}
    }

    public static int countBatchSize(List<Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) return 0;

        try {
            return objectMapper.writeValueAsBytes(metrics).length;
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    private void markFileAsDone(Path path) {
        try {
            String name = path.getFileName().toString();
            if (name.contains("-done")) return;

            Files.move(
                path,
                path.resolveSibling(name.replace(".json", "") + "-done.json"),
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException ignore) {
        }
    }
}

