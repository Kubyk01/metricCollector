package com.deserializer;

import com.configuration.EnvVarProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.model.Metric;
import com.model.MetricComponent;
import com.model.MetricComponentOperationType;
import com.model.MetricType;
import com.model.exception.MisconfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class MetricDeserializer {
    private final ObjectMapper objectMapper;

    public MetricDeserializer() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public List<JsonNode> loadFiles(String location) {
        if (location.startsWith("classpath:")) {
            String folder = location.substring("classpath:".length());

            if (folder.startsWith("/")) {
                folder = folder.substring(1);
            }
            return loadFromClasspathFolder(folder);
        }

        if (location.startsWith("file:")) {
            String path =  location.substring("file:".length());
            return loadFromFileSystem(Paths.get(path));
        }

        return loadFromFileSystem(Paths.get(location));
    }

    private List<JsonNode> loadFromFileSystem(Path folderPath) {
        List<JsonNode> result = new ArrayList<>();

        try (Stream<Path> files = Files.walk(folderPath)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                try {
                    JsonNode rootNode = objectMapper.readTree(path.toFile());
                    result.add(rootNode);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read file: " + path, e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to load metrics from: " + folderPath, e);
        }

        return result;
    }

    private List<JsonNode> loadFromClasspathFolder(String folder) {
        List<JsonNode> result = new ArrayList<>();

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL url = classLoader.getResource(folder);

            if (url == null) {
                throw new RuntimeException("Could not find resource folder: " + folder);
            }

            if ("file".equals(url.getProtocol())) {
                Path path = Paths.get(url.toURI());
                return loadFromFileSystem(path);
            }

            if ("jar".equals(url.getProtocol())) {
                String urlPath = url.getPath();
                String jarPath = urlPath.substring(5, urlPath.indexOf("!"));

                try (JarFile jarFile = new JarFile(URLDecoder.decode(jarPath, String.valueOf(StandardCharsets.UTF_8)))) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();

                        if (name.startsWith(folder + "/") && name.endsWith(".json") && !entry.isDirectory()) {
                            try (InputStream is = jarFile.getInputStream(entry)) {
                                JsonNode rootNode = objectMapper.readTree(is);
                                result.add(rootNode);
                            }
                        }
                    }
                }
                return result;
            }

            throw new MisconfigurationException("Unsupported protocol for classpath folder: " + url.getProtocol());
        } catch (IOException | URISyntaxException e) {
            throw new MisconfigurationException("Error loading metric configs from classpath folder: " + folder);
        }
    }

    public Metric deserializeMetric(JsonNode metricNode) {
        List<MetricComponent> components = new ArrayList<>();
        for (JsonNode compNode : metricNode.get("components")) {
            components.add(deserializeComponent(compNode));
        }

        List<String> tags = metricNode.has("tags")
            ? objectMapper.convertValue(metricNode.get("tags"), new TypeReference<List<String>>() {})
            : new ArrayList<>();

        return new Metric(
            metricNode.get("name").asText(),
            metricNode.get("unit").asText(),
            metricNode.get("origin").asText(),
            MetricType.valueOf(metricNode.get("type").asText()),
            metricNode.has("description") ? metricNode.get("description").asText() : null,
            tags,
            components
        );
    }

    private MetricComponent deserializeComponent(JsonNode node) {
        ZonedDateTime timestamp = ZonedDateTime.parse("1997-06-02T15:10:00.000Z");
        if (!node.get("timestamp").asText().contains("dynamic")) {
            timestamp = ZonedDateTime.parse(node.get("timestamp").asText());
        }

        String key = "";
        if (node.has("key") && !node.get("key").asText().contains("dynamic")) {
            key = node.get("key").asText();
        }

        String value = null;
        if (!node.get("value").asText().contains("dynamic")) {
            value = node.get("value").asText();
        }

        return new MetricComponent(
            node.get("name").asText(),
            timestamp,
            key,
            value,
            MetricComponentOperationType.valueOf(node.get("operation").asText()),
            deserializeTags(node.get("tags")),
            node.has("evaluationOrder") ? node.get("evaluationOrder").asLong() : null
        );
    }

    private List<String> deserializeTags(JsonNode node) {
        if (node == null || !node.isArray()) return new ArrayList<>();

        List<String> tags = new ArrayList<>();

        for (JsonNode tagNode : node) {
            String tagValue = tagNode.asText();
            if (!tagValue.contains("dynamic")) {
                tags.add(tagValue);
            }
        }
        return tags;
    }
}
