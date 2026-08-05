package com.configuration;

import com.model.exception.MisconfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class EnvVarProvider {
    private static final String env = System.getenv("metric-collector-library.env");

    private static final Properties properties = loadProperties();

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

}
