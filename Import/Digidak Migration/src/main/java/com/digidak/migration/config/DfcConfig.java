package com.digidak.migration.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration class for Documentum DFC settings
 */
public class DfcConfig {
    private static final String DEFAULT_DFC_CONFIG_PATH = "config/dfc.properties";

    private Properties dfcProperties;

    // DFC Configuration keys
    private static final String DOCBROKER_HOST = "dfc.docbroker.host";
    private static final String DOCBROKER_PORT = "dfc.docbroker.port";
    private static final String REPOSITORY_NAME = "dfc.repository.name";
    private static final String USERNAME = "dfc.username";
    private static final String PASSWORD = "dfc.password";
    private static final String SESSION_POOL_SIZE = "dfc.session.pool.size";

    // Global Registry Configuration keys
    private static final String GLOBAL_REGISTRY_REPOSITORY = "dfc.globalregistry.repository";
    private static final String GLOBAL_REGISTRY_USERNAME = "dfc.globalregistry.username";
    private static final String GLOBAL_REGISTRY_PASSWORD = "dfc.globalregistry.password";

    public DfcConfig() throws IOException {
        this(DEFAULT_DFC_CONFIG_PATH);
    }

    public DfcConfig(String configPath) throws IOException {
        dfcProperties = new Properties();
        try (InputStream input = new FileInputStream(configPath)) {
            dfcProperties.load(input);
        }

        // Set system properties for DFC
        System.getProperties().putAll(dfcProperties);
    }

    public String getDocbrokerHost() {
        // Try with array index first, then without
        String host = dfcProperties.getProperty(DOCBROKER_HOST + "[0]");
        if (host == null || host.trim().isEmpty()) {
            host = dfcProperties.getProperty(DOCBROKER_HOST);
        }
        return host;
    }

    public int getDocbrokerPort() {
        // Try with array index first, then without
        String port = dfcProperties.getProperty(DOCBROKER_PORT + "[0]");
        if (port == null || port.trim().isEmpty()) {
            port = dfcProperties.getProperty(DOCBROKER_PORT, "1489");
        }
        return Integer.parseInt(port);
    }

    public String getRepositoryName() {
        // Try dfc.repository.name first, then dfc.repository, then global registry
        String repoName = dfcProperties.getProperty(REPOSITORY_NAME);
        if (repoName == null || repoName.trim().isEmpty()) {
            repoName = dfcProperties.getProperty("dfc.repository");
        }
        if (repoName == null || repoName.trim().isEmpty()) {
            repoName = dfcProperties.getProperty(GLOBAL_REGISTRY_REPOSITORY);
        }
        return repoName;
    }

    public String getUsername() {
        // Try direct username first, then global registry
        String username = dfcProperties.getProperty(USERNAME);
        if (username == null || username.trim().isEmpty()) {
            username = dfcProperties.getProperty(GLOBAL_REGISTRY_USERNAME);
        }
        return username;
    }

    public String getPassword() {
        // Try direct password first, then global registry
        String password = dfcProperties.getProperty(PASSWORD);
        if (password == null || password.trim().isEmpty()) {
            password = dfcProperties.getProperty(GLOBAL_REGISTRY_PASSWORD);
        }
        return password;
    }

    public int getSessionPoolSize() {
        return Integer.parseInt(dfcProperties.getProperty(SESSION_POOL_SIZE, "10"));
    }

    public Properties getDfcProperties() {
        return new Properties(dfcProperties);
    }
}
