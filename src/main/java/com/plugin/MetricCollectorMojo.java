package com.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javassist.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "instrument", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class MetricCollectorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private File outputDirectory;

    @Parameter(defaultValue = "src/main/resources/collector-metrics-config", required = true)
    private String configLocation;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("MetricCollector: instrumenting classes...");

        List<Path> configFiles = findConfigFiles();
        if (configFiles.isEmpty()) {
            getLog().warn("No metric configuration files found at: " + configLocation);
            return;
        }

        List<Instruction> instructions = new ArrayList<>();
        for (Path file : configFiles) {
            try {
                JsonNode root = mapper.readTree(file.toFile());
                JsonNode metrics = root.get("metrics");
                if (metrics == null || !metrics.isArray()) continue;
                for (JsonNode metricNode : metrics) {
                    String metricName = metricNode.get("name").asText();
                    String unit = metricNode.get("unit").asText();
                    String origin = metricNode.get("origin").asText();
                    String type = metricNode.get("type").asText();
                    String description = metricNode.has("description") ? metricNode.get("description").asText() : null;
                    List<String> tags = metricNode.has("tags")
                            ? mapper.convertValue(metricNode.get("tags"), List.class)
                            : Collections.emptyList();

                    JsonNode components = metricNode.get("components");
                    if (components == null) continue;
                    for (JsonNode comp : components) {
                        String compName = comp.get("name").asText();
                        String triggerExpr = comp.has("trigger") ? comp.get("trigger").asText() : null;
                        String valueExpr = comp.has("value") ? comp.get("value").asText() : null;
                        if (triggerExpr == null || valueExpr == null) continue;

                        Instruction instr = parseInstruction(metricName, compName, triggerExpr, valueExpr,
                                unit, origin, type, description, tags);
                        if (instr != null) {
                            instructions.add(instr);
                        }
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read config file: " + file, e);
            }
        }

        if (instructions.isEmpty()) {
            getLog().info("No instrumentable components found.");
            return;
        }

        ClassPool pool = new ClassPool();
        try {
            pool.appendClassPath(outputDirectory.getAbsolutePath());
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
        pool.appendSystemPath();

        for (Instruction instr : instructions) {
            try {
                CtClass ctClass = pool.get(instr.className);
                if (ctClass.isFrozen()) ctClass.defrost();

                CtMethod method = ctClass.getDeclaredMethod(instr.methodName);
                if (method != null) {
                    insertReportCall(method, instr);
                    getLog().info("Instrumented method " + instr.className + "." + instr.methodName);
                }

                String setterName = "set" + capitalize(instr.fieldName);
                try {
                    CtMethod setter = ctClass.getDeclaredMethod(setterName);
                    insertReportCall(setter, instr);
                    getLog().info("Instrumented setter " + instr.className + "." + setterName);
                } catch (NotFoundException ignored) {}

                ctClass.writeFile(outputDirectory.getAbsolutePath());
                ctClass.detach();
            } catch (NotFoundException | CannotCompileException | IOException e) {
                getLog().error("Failed to instrument class " + instr.className, e);
            }
        }

        getLog().info("MetricCollector instrumentation complete.");
    }

    private void insertReportCall(CtMethod method, Instruction instr) throws CannotCompileException {
        String code = String.format(
                "com.collector.MetricCollector.report(\"%s\", \"%s\", this.%s, \"%s\", java.util.Collections.emptyList());",
                instr.metricName,
                instr.componentName,
                instr.fieldName,
                instr.key != null ? instr.key : "default"
        );
        method.insertBefore(code);
    }

    private Instruction parseInstruction(String metricName, String componentName,
                                         String triggerExpr, String valueExpr,
                                         String unit, String origin, String type,
                                         String description, List<String> tags) {
        try {
            int lastDot = triggerExpr.lastIndexOf('.');
            if (lastDot == -1) return null;
            String className = triggerExpr.substring(0, lastDot);
            String methodPart = triggerExpr.substring(lastDot + 1);
            String methodName = methodPart.endsWith("()") ? methodPart.substring(0, methodPart.length() - 2) : methodPart;

            int lastDotValue = valueExpr.lastIndexOf('.');
            if (lastDotValue == -1) return null;
            String valueClassName = valueExpr.substring(0, lastDotValue);
            String fieldName = valueExpr.substring(lastDotValue + 1);
            if (!className.equals(valueClassName)) {
                getLog().warn("Class mismatch between trigger and value");
                return null;
            }

            String key = componentName;
            return new Instruction(className, methodName, fieldName, key,
                    metricName, componentName, unit, origin, type, description, tags);
        } catch (Exception e) {
            getLog().warn("Failed to parse instruction from trigger=" + triggerExpr + ", value=" + valueExpr, e);
            return null;
        }
    }

    private List<Path> findConfigFiles() throws MojoExecutionException {
        Path configDir = Paths.get(project.getBasedir().getAbsolutePath(), configLocation);
        if (!Files.exists(configDir)) {
            try {
                File resourceDir = new File(outputDirectory, "../classes/collector-metrics-config");
                if (resourceDir.exists()) configDir = resourceDir.toPath();
                else return Collections.emptyList();
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        try (Stream<Path> walk = Files.walk(configDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan config directory", e);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static class Instruction {
        final String className, methodName, fieldName, key;
        final String metricName, componentName;
        final String unit, origin, type, description;
        final List<String> tags;
        Instruction(String className, String methodName, String fieldName, String key,
                    String metricName, String componentName,
                    String unit, String origin, String type,
                    String description, List<String> tags) {
            this.className = className;
            this.methodName = methodName;
            this.fieldName = fieldName;
            this.key = key;
            this.metricName = metricName;
            this.componentName = componentName;
            this.unit = unit;
            this.origin = origin;
            this.type = type;
            this.description = description;
            this.tags = tags;
        }
    }
}
