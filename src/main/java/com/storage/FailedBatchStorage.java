package com.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.model.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FailedBatchStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailedBatchStorage.class);

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .enable(SerializationFeature.INDENT_OUTPUT);

    protected static final Path STORAGE_DIR_RETRY = Paths.get("retry-send");
    protected static final Path STORAGE_DIR_FAILED = Paths.get("failed-batches");

    private static final Duration CLEANUP_INTERVAL = Duration.ofHours(6);

    private static final Disposable CLEANUP_TASK;

    static {
        try {
            Files.createDirectories(STORAGE_DIR_RETRY);
            Files.createDirectories(STORAGE_DIR_FAILED);
        } catch (IOException e) {
            LOGGER.error("Could not create storage directory", e);
        }

        CLEANUP_TASK = Flux.interval(Duration.ZERO, CLEANUP_INTERVAL)
            .publishOn(Schedulers.boundedElastic())
            .subscribe(
                tick -> deleteDoneAndOldFiles(),
                error -> LOGGER.error("Cleanup scheduler failed", error)
            );
    }

    private FailedBatchStorage() {}


    public static void save(List<Metric> metrics, String reason) {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss-SS"));

        String filename = String.format("%s_%s.json", reason, timestamp);

        Path directory = isPermanentFailure(filename)
            ? STORAGE_DIR_FAILED
            : STORAGE_DIR_RETRY;

        try {
            objectMapper.writeValue(
                directory.resolve(filename).toFile(),
                metrics
            );
        } catch (IOException e) {
            LOGGER.error("Could not save failed batch {}", filename, e);
        }
    }

    private static boolean isPermanentFailure(String filename) {
        return filename.contains("single-metric-too-large")
            || filename.contains("json-processing-exception")
            || filename.contains("sub-batch-too-large")
            || filename.contains("bad-request")
            || filename.contains("forbidden")
            || filename.contains("not-found");
    }

    private static void deleteDoneAndOldFiles() {
        deleteFromDir(STORAGE_DIR_RETRY);
        deleteFromDir(STORAGE_DIR_FAILED);
    }

    private static void deleteFromDir(Path directory) {
        try (Stream<Path> paths = Files.list(directory)) {

            paths.filter(Files::isRegularFile)
                .forEach(path -> {

                    LocalDateTime fileDate =
                        extractTimestampFromFileName(
                            path.getFileName().toString()
                        );

                    if (path.getFileName().toString().contains("-done")
                        || (fileDate != null &&
                        fileDate.isBefore(LocalDateTime.now().minusMonths(3)))) {

                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            LOGGER.error("Could not delete {}", path, e);
                        }
                    }
                });

        } catch (IOException e) {
            LOGGER.error("Cleanup error", e);
        }
    }

    private static LocalDateTime extractTimestampFromFileName(String fileName) {
        try {
            Pattern pattern = Pattern.compile(
                "\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}-\\d{2}"
            );

            Matcher matcher = pattern.matcher(fileName);

            if (matcher.find()) {
                return LocalDateTime.parse(
                    matcher.group(),
                    DateTimeFormatter.ofPattern(
                        "yyyy-MM-dd'T'HH-mm-ss-SS"
                    )
                );
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    public static void shutdown() {
        CLEANUP_TASK.dispose();
    }
}