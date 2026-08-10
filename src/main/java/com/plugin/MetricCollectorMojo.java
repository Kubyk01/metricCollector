package com.plugin;

import com.configuration.EnvVarProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
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

    private boolean scanAllClasses;

    @Override
    public void execute() throws MojoExecutionException {
        scanAllClasses = EnvVarProvider.isScanAllClasses();
        getLog().info("MetricCollector: instrumenting classes... scanAllClasses=" + scanAllClasses);

        List<Path> configFiles = findConfigFiles();
        if (configFiles.isEmpty()) {
            getLog().warn("No metric configuration files found at: " + configLocation);
            return;
        }

        List<Instruction> allInstructions = new ArrayList<>();
        for (Path file : configFiles) {
            try {
                MetricDeserializedComponent.MetricConfig config = mapper.readValue(file.toFile(), MetricDeserializedComponent.MetricConfig.class);
                if (config.metrics == null) continue;
                for (MetricDeserializedComponent.MetricDef metric : config.metrics) {
                    if (metric.components == null) continue;
                    for (MetricDeserializedComponent comp : metric.components) {
                        if (comp.getTrigger() != null || comp.getValue() != null) {
                            String metricName = metric.name;
                            Instruction instr = buildInstruction(comp, metricName);
                            if (instr != null) {
                                allInstructions.add(instr);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read config file: " + file, e);
            }
        }

        if (allInstructions.isEmpty()) {
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

        List<Instruction> explicitInstructions = allInstructions.stream()
            .filter(instr -> instr.className != null)
            .collect(Collectors.toList());

        List<Instruction> globalInstructions = allInstructions.stream()
            .filter(instr -> instr.className == null && instr.fieldName != null && instr.triggerMethodName == null)
            .collect(Collectors.toList());

        for (Instruction instr : explicitInstructions) {
            instrumentClass(pool, instr);
        }

        if (scanAllClasses && !globalInstructions.isEmpty()) {
            Map<String, List<Instruction>> fieldToInstructions = globalInstructions.stream()
                .collect(Collectors.groupingBy(instr -> instr.fieldName));

            getLog().info("Scanning all classes for fields: " + fieldToInstructions.keySet());

            try {
                List<CtClass> allClasses = findAllClasses(pool, outputDirectory.toPath());
                for (CtClass ctClass : allClasses) {
                    if (ctClass.isFrozen()) ctClass.defrost();
                    String className = ctClass.getName();
                    boolean modified = false;

                    for (Map.Entry<String, List<Instruction>> entry : fieldToInstructions.entrySet()) {
                        String fieldName = entry.getKey();
                        try {
                            CtField field = ctClass.getDeclaredField(fieldName);
                            if (field != null) {
                                instrumentFieldChangesGlobal(ctClass, fieldName, entry.getValue());
                                modified = true;
                            }
                        } catch (NotFoundException ignored) {
                        }
                    }

                    if (modified) {
                        ctClass.writeFile(outputDirectory.getAbsolutePath());
                        getLog().debug("Instrumented class " + className);
                    }
                    ctClass.detach();
                }
            } catch (Exception e) {
                getLog().error("Failed during all-classes scanning", e);
            }
        }

        getLog().info("MetricCollector instrumentation complete.");
    }

    private void instrumentClass(ClassPool pool, Instruction instr) throws MojoExecutionException {
        try {
            CtClass ctClass = pool.get(instr.className);
            if (ctClass.isFrozen()) ctClass.defrost();

            if (instr.triggerMethodName != null) {
                try {
                    CtMethod method = ctClass.getDeclaredMethod(instr.triggerMethodName);
                    if (!Modifier.isAbstract(method.getModifiers())) {
                        insertReportCall(method, instr);
                        getLog().info("Instrumented trigger method " + instr.className + "." + instr.triggerMethodName);
                    }
                } catch (NotFoundException e) {
                    getLog().warn("Trigger method not found: " + instr.className + "." + instr.triggerMethodName);
                }
            }

            if (instr.fieldName != null && instr.triggerMethodName == null) {
                instrumentFieldChanges(ctClass, instr);
            }

            ctClass.writeFile(outputDirectory.getAbsolutePath());
            ctClass.detach();
        } catch (NotFoundException | CannotCompileException | IOException e) {
            throw new MojoExecutionException("Failed to instrument class " + instr.className, e);
        }
    }

    private Instruction buildInstruction(MetricDeserializedComponent comp, String metricName) {
        try {
            String triggerExpr = comp.getTrigger();
            String valueExpr = comp.getValue();
            String operationTrigger = comp.getOperation_trigger();
            if (operationTrigger == null || operationTrigger.isEmpty()) {
                operationTrigger = "changed";
            }

            String className = null;
            String methodName = null;
            String fieldName = null;

            if (triggerExpr != null) {
                int lastDot = triggerExpr.lastIndexOf('.');
                if (lastDot == -1) return null;
                className = triggerExpr.substring(0, lastDot);
                String methodPart = triggerExpr.substring(lastDot + 1);
                methodName = methodPart.endsWith("()") ? methodPart.substring(0, methodPart.length() - 2) : methodPart;
            }

            if (valueExpr != null) {
                int lastDot = valueExpr.lastIndexOf('.');
                if (lastDot == -1) return null;
                String valClassName = valueExpr.substring(0, lastDot);
                fieldName = valueExpr.substring(lastDot + 1);

                if (className != null && !className.equals(valClassName)) {
                    getLog().warn("Class mismatch between trigger and value: " + triggerExpr + " vs " + valueExpr);
                    return null;
                }
                if (className == null && !scanAllClasses) {
                    className = valClassName;
                }
            }

            if (className == null && fieldName == null) {
                return null;
            }

            return new Instruction(
                className, methodName, fieldName,
                metricName, comp.getName(), operationTrigger
            );
        } catch (Exception e) {
            getLog().warn("Failed to parse instruction from component: " + comp.getName(), e);
            return null;
        }
    }

    private void insertReportCall(CtMethod method, Instruction instr) throws CannotCompileException {
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        String fieldAccess;
        if (instr.fieldName != null) {
            if (isStatic) {
                fieldAccess = instr.className + "." + instr.fieldName;
            } else {
                fieldAccess = "this." + instr.fieldName;
            }
        } else {
            fieldAccess = "null";
        }

        String code = String.format(
            "com.collector.MetricCollector.submit(\"%s\", \"%s\", %s)" +
                ".subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).subscribe();",
            instr.metricName,
            instr.componentName,
            fieldAccess
        );
        method.insertBefore(code);
    }

    private void instrumentFieldChanges(CtClass ctClass, Instruction instr) throws CannotCompileException, NotFoundException {
        String fieldName = instr.fieldName;
        String className = ctClass.getName();
        CtField field = ctClass.getDeclaredField(fieldName);
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        String fieldType = field.getType().getName();

        String comparisonExpr = generateComparisonExpression(fieldType, instr.operationTrigger);
        String submitCall = String.format(
            "com.collector.MetricCollector.submit(\"%s\", \"%s\", newValue)" +
                ".subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).subscribe();",
            instr.metricName,
            instr.componentName
        );

        String replacement = buildReplacement(isStatic, className, fieldName, fieldType, comparisonExpr, submitCall);
        applyFieldWriteInstrumentation(ctClass, className, fieldName, replacement);
    }

    private void instrumentFieldChangesGlobal(CtClass ctClass, String fieldName, List<Instruction> instructions)
        throws CannotCompileException, NotFoundException {
        String className = ctClass.getName();
        CtField field = ctClass.getDeclaredField(fieldName);
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        String fieldType = field.getType().getName();

        StringBuilder body = new StringBuilder();
        for (Instruction instr : instructions) {
            String comparisonExpr = generateComparisonExpression(fieldType, instr.operationTrigger);
            String submitCall = String.format(
                "com.collector.MetricCollector.submit(\"%s\", \"%s\", newValue)" +
                    ".subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).subscribe();",
                instr.metricName,
                instr.componentName
            );
            body.append("if (").append(comparisonExpr).append(") { ")
                .append(submitCall)
                .append(" } ");
        }

        String replacement = buildReplacement(isStatic, className, fieldName, fieldType, "true", body.toString());
        applyFieldWriteInstrumentation(ctClass, className, fieldName, replacement);
    }

    private String buildReplacement(boolean isStatic, String className, String fieldName,
                                    String fieldType, String condition, String action) {
        if (isStatic) {
            return String.format(
                "{ %s oldValue = %s.%s; $proceed($$); %s newValue = %s.%s; if (%s) { %s } }",
                fieldType, className, fieldName,
                fieldType, className, fieldName,
                condition, action
            );
        } else {
            return String.format(
                "{ %s oldValue = $0.%s; $proceed($$); %s newValue = $0.%s; if (%s) { %s } }",
                fieldType, fieldName,
                fieldType, fieldName,
                condition, action
            );
        }
    }

    private void applyFieldWriteInstrumentation(CtClass ctClass, String className, String fieldName, String replacement)
        throws CannotCompileException {
        CtBehavior[] behaviors = ctClass.getDeclaredBehaviors();
        for (CtBehavior behavior : behaviors) {
            if (behavior.isEmpty()) continue;
            behavior.instrument(new ExprEditor() {
                public void edit(FieldAccess f) throws CannotCompileException {
                    if (f.isWriter() && f.getFieldName().equals(fieldName) && f.getClassName().equals(className)) {
                        f.replace(replacement);
                    }
                }
            });
        }
    }

    private String generateComparisonExpression(String fieldType, String operationTrigger) {
        boolean isNumeric = isNumericType(fieldType);
        boolean isBoolean = "boolean".equals(fieldType) || "java.lang.Boolean".equals(fieldType);

        if (isNumeric) {
            switch (operationTrigger.toLowerCase()) {
                case "increase": return "newValue > oldValue";
                case "decrease": return "newValue < oldValue";
                default: return "newValue != oldValue";
            }
        } else if (isBoolean) {
            return "newValue != oldValue";
        } else {
            return "(oldValue == null ? newValue != null : !oldValue.equals(newValue))";
        }
    }

    private boolean isNumericType(String type) {
        return type.equals("byte") || type.equals("short") || type.equals("int") || type.equals("long") ||
            type.equals("float") || type.equals("double") ||
            type.equals("java.lang.Byte") || type.equals("java.lang.Short") ||
            type.equals("java.lang.Integer") || type.equals("java.lang.Long") ||
            type.equals("java.lang.Float") || type.equals("java.lang.Double");
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

    private List<CtClass> findAllClasses(ClassPool pool, Path root) throws Exception {
        List<CtClass> classes = new ArrayList<>();
        Files.walk(root)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".class"))
            .forEach(p -> {
                try {
                    String relative = root.relativize(p).toString();
                    String className = relative.replace(File.separatorChar, '.')
                        .replace(".class", "");
                    CtClass ct = pool.get(className);
                    classes.add(ct);
                } catch (NotFoundException e) {
                    getLog().warn("Could not load class: " + p, e);
                }
            });
        return classes;
    }

    private static class Instruction {
        final String className;
        final String triggerMethodName;
        final String fieldName;
        final String metricName;
        final String componentName;
        final String operationTrigger;

        Instruction(String className, String triggerMethodName, String fieldName,
                    String metricName, String componentName, String operationTrigger) {
            this.className = className;
            this.triggerMethodName = triggerMethodName;
            this.fieldName = fieldName;
            this.metricName = metricName;
            this.componentName = componentName;
            this.operationTrigger = operationTrigger;
        }
    }
}