package io.github.kubyk01;

import io.github.kubyk01.collector.MetricCollector;
import io.github.kubyk01.configuration.EnvVarProvider;
import io.github.kubyk01.deserializer.MetricIdCache;
import io.github.kubyk01.fixture.MetricComponentFixture;
import io.github.kubyk01.fixture.MetricFixture;
import io.github.kubyk01.model.Metric;
import io.github.kubyk01.model.MetricComponent;
import io.github.kubyk01.port.secondary.MetricPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientTest {

    static {
        System.setProperty("metric-collector-library.env", "local");
    }

    private static MockedStatic<EnvVarProvider> envMock;
    private MetricPort mockMetricPort;

    @BeforeAll
    static void init() {
        envMock = mockStatic(EnvVarProvider.class);
        envMock.when(EnvVarProvider::getBatchSize).thenReturn(0);
        envMock.when(EnvVarProvider::getProtocol).thenReturn("http");
        envMock.when(EnvVarProvider::getBaseUrl).thenReturn("http://localhost");
        envMock.when(EnvVarProvider::getToken).thenReturn(null);
        envMock.when(EnvVarProvider::getMetricsConfigLocation)
            .thenReturn("classpath:collector-metrics-config/local");
        envMock.when(EnvVarProvider::getGrpcUsePlaintext).thenReturn(true);
        envMock.when(EnvVarProvider::getRetryInterval).thenReturn(java.time.Duration.ofSeconds(30));
    }

    @AfterAll
    static void tearDown() {
        envMock.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMetricPort = mock(MetricPort.class);
        lenient().when(mockMetricPort.sendMetric(any())).thenReturn(Mono.empty());
        lenient().when(mockMetricPort.sendMetrics(anyList())).thenReturn(Mono.empty());
        lenient().when(mockMetricPort.sendComponentsValues(anyMap())).thenReturn(Mono.empty());

        Field portField = MetricCollector.class.getDeclaredField("metricPort");
        portField.setAccessible(true);
        portField.set(null, mockMetricPort);

        MetricIdCache cache = new MetricIdCache();
        UUID id = UUID.randomUUID();
        cache.putMetric("test-metric", id);
        Mono<MetricIdCache> cacheMono = Mono.just(cache);
        Field cacheField = MetricCollector.class.getDeclaredField("cacheMono");
        cacheField.setAccessible(true);
        cacheField.set(null, cacheMono);

        Field bufferField = MetricCollector.class.getDeclaredField("batchBuffer");
        bufferField.setAccessible(true);
        bufferField.set(null, new ConcurrentHashMap<>());

        Field shutdownField = MetricCollector.class.getDeclaredField("shutdown");
        shutdownField.setAccessible(true);
        shutdownField.set(null, new AtomicBoolean(false));
    }

    @Test
    void submitMetric_shouldCallSendMetric() {
        Metric metric = new MetricFixture().build();
        MetricCollector.submit(metric);
        verify(mockMetricPort).sendMetric(metric);
    }

    @Test
    void submitMetricIdAndComponents_shouldCallSendMetricsComponents() {
        UUID id = UUID.randomUUID();
        List<MetricComponent> comps = Collections.singletonList(new MetricComponentFixture().build());
        MetricCollector.submit(id, comps);
        verify(mockMetricPort).sendMetricsComponents(id, comps);
    }

    @Test
    void submitByNameAndComponent_withCache_shouldCallSendComponentsValues() throws Exception {
        Field cacheField = MetricCollector.class.getDeclaredField("cacheMono");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Mono<MetricIdCache> cacheMono = (Mono<MetricIdCache>) cacheField.get(null);
        MetricIdCache cache = cacheMono.block();
        UUID expectedId = cache.getMetricIdByMetricName("test-metric");

        String componentName = "testComp";
        Object value = 123;

        MetricCollector.submit("test-metric", componentName, value).block();

        ArgumentCaptor<Map<UUID, List<MetricComponent>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockMetricPort).sendComponentsValues(captor.capture());

        Map<UUID, List<MetricComponent>> sentMap = captor.getValue();
        assertEquals(1, sentMap.size());
        assertTrue(sentMap.containsKey(expectedId));
        List<MetricComponent> comps = sentMap.get(expectedId);
        assertEquals(1, comps.size());
        assertEquals(componentName, comps.get(0).getName());
        assertEquals("123", comps.get(0).getValue());
    }

    @Test
    void submitByNameAndComponent_cacheMiss_shouldThrowError() throws Exception {
        MetricIdCache emptyCache = new MetricIdCache();
        Mono<MetricIdCache> emptyMono = Mono.just(emptyCache);
        Field cacheField = MetricCollector.class.getDeclaredField("cacheMono");
        cacheField.setAccessible(true);
        cacheField.set(null, emptyMono);

        assertThrows(IllegalArgumentException.class, () ->
            MetricCollector.submit("unknown", "comp", 1).block()
        );
    }

    @Test
    void sendMetricsRetry_shouldCallSendMetrics() {
        List<Metric> metrics = Collections.singletonList(new MetricFixture().build());
        MetricCollector.sendMetricsRetry(metrics).block();
        verify(mockMetricPort).sendMetrics(metrics);
    }
}