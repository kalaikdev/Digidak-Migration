package com.digidak.migration.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration class for migration settings
 */
public class MigrationConfig {
    private static final String DEFAULT_CONFIG_PATH = "config/migration.properties";

    private Properties properties;

    // Configuration keys
    private static final String THREAD_POOL_SIZE = "migration.threadpool.size";
    private static final String BATCH_SIZE = "migration.batch.size";
    private static final String RETRY_ATTEMPTS = "migration.retry.attempts";
    private static final String RETRY_DELAY_MS = "migration.retry.delay.ms";
    private static final String DATA_EXPORT_PATH = "migration.data.export.path";
    private static final String SCHEMA_PATH = "migration.schema.path";
    private static final String CABINET_NAME = "migration.cabinet.name";

    public MigrationConfig() throws IOException {
        this(DEFAULT_CONFIG_PATH);
    }

    public MigrationConfig(String configPath) throws IOException {
        properties = new Properties();
        try (InputStream input = new FileInputStream(configPath)) {
            properties.load(input);
        }
    }

    public int getThreadPoolSize() {
        return Integer.parseInt(properties.getProperty(THREAD_POOL_SIZE,
            String.valueOf(Runtime.getRuntime().availableProcessors() * 2)));
    }

    public int getBatchSize() {
        return Integer.parseInt(properties.getProperty(BATCH_SIZE, "10"));
    }

    public int getRetryAttempts() {
        return Integer.parseInt(properties.getProperty(RETRY_ATTEMPTS, "3"));
    }

    public long getRetryDelayMs() {
        return Long.parseLong(properties.getProperty(RETRY_DELAY_MS, "1000"));
    }

    public String getDataExportPath() {
        return properties.getProperty(DATA_EXPORT_PATH, "DigidakMetadata_Export");
    }

    public String getSchemaPath() {
        return properties.getProperty(SCHEMA_PATH, "DocumentumSchema/object model.csv");
    }

    public String getCabinetName() {
        return properties.getProperty(CABINET_NAME, "Digidak Legacy");
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
