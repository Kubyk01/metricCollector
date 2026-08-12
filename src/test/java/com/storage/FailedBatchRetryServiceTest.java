package com.storage;

import com.fixture.MetricFixture;
import com.model.Metric;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class FailedBatchRetryServiceTest {

    private MockedStatic<FailedBatchStorage> storageMock;

    @BeforeEach
    void setUp() {
        storageMock = mockStatic(FailedBatchStorage.class);
    }

    @AfterEach
    void tearDown() {
        storageMock.close();
    }

    private static String repeat(int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append('a');
        }
        return sb.toString();
    }

    @Test
    void splitAndPrepareSubBatches_whenBatchIsTooLarge_shouldSplitIntoSmallSubbatches() {
        String bigDescription = repeat(150_000);
        List<Metric> metrics = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            metrics.add(new MetricFixture().withDescription(bigDescription).build());
        }
        int singleSize = FailedBatchRetryService.countBatchSize(Collections.singletonList(metrics.get(0)));
        assertTrue(singleSize > FailedBatchRetryService.MAX_BATCH_SIZE / 2);
        assertTrue(singleSize <= FailedBatchRetryService.MAX_BATCH_SIZE);

        List<List<Metric>> subbatches = FailedBatchRetryService.splitAndPrepareSubBatches(metrics);

        int totalMetrics = subbatches.stream().mapToInt(List::size).sum();
        assertEquals(10, totalMetrics);
        assertEquals(10, subbatches.size());

        for (List<Metric> sub : subbatches) {
            assertTrue(FailedBatchRetryService.countBatchSize(sub) <= FailedBatchRetryService.MAX_BATCH_SIZE);
        }
    }

    @Test
    void splitAndPrepareSubBatches_whenOneMetricIsTooLarge_shouldNotSplitIntoSmallSubbatches() {
        List<Metric> singleList = Collections.singletonList(
            new MetricFixture().withDescription(repeat(300_000)).build()
        );
        assertTrue(FailedBatchRetryService.countBatchSize(singleList) > FailedBatchRetryService.MAX_BATCH_SIZE);

        List<List<Metric>> subbatches = FailedBatchRetryService.splitAndPrepareSubBatches(singleList);

        assertEquals(0, subbatches.size());

        storageMock.verify(() ->
            FailedBatchStorage.save(anyList(), eq("single-metric-too-large"))
        );
    }
}
