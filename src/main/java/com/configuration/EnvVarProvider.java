package com.configuration;

import com.model.exception.MisconfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class EnvVarProvider {
    private static final String env = System.getenv("metric-collector-library.env");

    private static final Properties properties = loadProperties();

    public static String getEnvironment() {
        return env;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = EnvVarProvider.class.getClassLoader()
            .getResourceAsStream("metric-collector-library.properties")) {

            if (input == null) {
                throw new MisconfigurationException("Unable to find metric-collector-library.properties file in classpath");
            }

            properties.load(input);
        } catch (IOException e) {
            throw new MisconfigurationException("Error loading metric-collector-library.properties: " + e.getMessage());
        }
        return properties;
    }

    public static String getBaseUrl() {
        VarEnvironments environment = VarEnvironments.parse(env);
        String propertyKey = String.format("base.url.%s", environment.name().toLowerCase());
        String baseUrl = properties.getProperty(propertyKey);

        if (baseUrl == null) {
            throw new MisconfigurationException("Missing base URL configuration for environment: '" + env + "' in properties file");
        }
        return baseUrl;
    }

    public static String getToken() {
        VarEnvironments environment = VarEnvironments.parse(env);
        String propertyKey = String.format("token.url.%s", environment.name().toLowerCase());
        String tokenUrl = properties.getProperty(propertyKey);

        if (tokenUrl == null) {
            throw new MisconfigurationException("Missing token configuration for environment: '" + env + "' in properties file");
        }
        return tokenUrl;
    }

    public static String getMetricsConfigLocation() {
        VarEnvironments environment = VarEnvironments.parse(env);
        String propertyKey = String.format("metrics-config-location.%s", environment.name().toLowerCase());
        String location = properties.getProperty(propertyKey);

        if (location == null) {
            throw new MisconfigurationException("Missing metrics config location for environment: '" + env + "' in properties file");
        }

        return location;
    }

    public static String getProtocol() {
        VarEnvironments environment = VarEnvironments.parse(env);
        String propertyKey = String.format("protocol.%s", environment.name().toLowerCase());
        String protocol = properties.getProperty(propertyKey);

        if (protocol == null) {
            return "grpc";
        }
        return protocol.trim().toLowerCase();
    }

    public static boolean isScanAllClasses() {
        VarEnvironments environment = VarEnvironments.parse(env);
        String propertyKey = String.format("scan.all.classes.%s", environment.name().toLowerCase());
        String value = properties.getProperty(propertyKey);

        return "true".equalsIgnoreCase(value);
    }

    public static int getBatchSize() {
        VarEnvironments environment = VarEnvironments.parse(env);
        String propertyKey = String.format("batch.size.%s", environment.name().toLowerCase());
        String value = properties.getProperty(propertyKey);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
