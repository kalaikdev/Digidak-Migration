package com.digidak.migration.repository;

import com.digidak.migration.config.DfcConfig;
import com.documentum.fc.client.IDfClient;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.IDfLoginInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Real Documentum DFC Session Manager with connection pooling
 * Connects to actual Documentum repository
 */
public class RealSessionManager {
    private static final Logger logger = LogManager.getLogger(RealSessionManager.class);

    private DfcConfig dfcConfig;
    private IDfSessionManager dfcSessionManager;
    private BlockingQueue<IDfSession> sessionPool;
    private int poolSize;
    private static RealSessionManager instance;

    private RealSessionManager(DfcConfig dfcConfig) throws DfException {
        this.dfcConfig = dfcConfig;
        this.poolSize = dfcConfig.getSessionPoolSize();
        this.sessionPool = new LinkedBlockingQueue<>(poolSize);
        initializeDfc();
        initializePool();
    }

    public static synchronized RealSessionManager getInstance(DfcConfig dfcConfig) throws DfException {
        if (instance == null) {
            instance = new RealSessionManager(dfcConfig);
        }
        return instance;
    }

    /**
     * Setup DFC environment - critical for DFC initialization
     */
    private void setupDfcEnvironment() {
        logger.info("Setting up DFC environment...");

        // Set DFC config file location
        String configPath = System.getProperty("user.dir") + "/config/dfc.properties";
        System.setProperty("dfc.properties.file", configPath);

        // Set DFC data directory
        String dataDir = dfcConfig.getDfcProperties().getProperty("dfc.data.dir", "D:/Documentum");
        System.setProperty("dfc.data.dir", dataDir);

        // Set docbroker primary
        System.setProperty("dfc.docbroker.host[0]", dfcConfig.getDocbrokerHost());
        System.setProperty("dfc.docbroker.port[0]", String.valueOf(dfcConfig.getDocbrokerPort()));

        // Set global registry if configured
        if (dfcConfig.getRepositoryName() != null) {
            System.setProperty("dfc.globalregistry.repository", dfcConfig.getRepositoryName());
        }
        if (dfcConfig.getUsername() != null) {
            System.setProperty("dfc.globalregistry.username", dfcConfig.getUsername());
        }
        if (dfcConfig.getPassword() != null) {
            System.setProperty("dfc.globalregistry.password", dfcConfig.getPassword());
        }

        // Disable BOF if not needed
        System.setProperty("dfc.bof.registry.connect.mode", "never");

        // Set session defaults
        System.setProperty("dfc.session.secure_connect_default", "try_native_first");

        logger.info("DFC environment configured:");
        logger.info("  Config file: {}", configPath);
        logger.info("  Data dir: {}", dataDir);
        logger.info("  Docbroker: {}:{}", dfcConfig.getDocbrokerHost(), dfcConfig.getDocbrokerPort());
    }

    /**
     * Initialize DFC client and session manager
     */
    private void initializeDfc() throws DfException {
        logger.info("Initializing Documentum DFC...");

        try {
            // Setup DFC environment before initializing client
            setupDfcEnvironment();

            // Get DFC client
            IDfClient client = com.documentum.fc.client.DfClient.getLocalClient();

            // Create session manager
            dfcSessionManager = client.newSessionManager();

            // Set login credentials
            IDfLoginInfo loginInfo = new com.documentum.fc.common.DfLoginInfo();
            loginInfo.setUser(dfcConfig.getUsername());
            loginInfo.setPassword(dfcConfig.getPassword());

            dfcSessionManager.setIdentity(dfcConfig.getRepositoryName(), loginInfo);

            logger.info("DFC initialized successfully");
            logger.info("Repository: {}", dfcConfig.getRepositoryName());
            logger.info("Docbroker: {}:{}", dfcConfig.getDocbrokerHost(), dfcConfig.getDocbrokerPort());

        } catch (DfException e) {
            logger.error("Failed to initialize DFC: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Initialize session pool
     */
    private void initializePool() throws DfException {
        logger.info("Initializing DFC session pool with size: {}", poolSize);

        try {
            for (int i = 0; i < poolSize; i++) {
                IDfSession session = dfcSessionManager.getSession(dfcConfig.getRepositoryName());
                if (session != null && session.isConnected()) {
                    sessionPool.offer(session);
                    logger.debug("Session {} created and added to pool", i + 1);
                } else {
                    throw new DfException("Failed to create session " + (i + 1));
                }
            }

            logger.info("DFC session pool initialized successfully with {} sessions", sessionPool.size());

        } catch (DfException e) {
            logger.error("Failed to initialize session pool: {}", e.getMessage(), e);
            // Cleanup any created sessions
            sessionPool.forEach(session -> {
                try {
                    dfcSessionManager.release(session);
                } catch (Exception ex) {
                    logger.warn("Error releasing session during cleanup", ex);
                }
            });
            throw e;
        }
    }

    /**
     * Get session from pool
     */
    public IDfSession getSession() throws InterruptedException {
        IDfSession session = sessionPool.poll(30, TimeUnit.SECONDS);
        if (session == null) {
            throw new RuntimeException("Timeout waiting for available session");
        }

        try {
            // Verify session is still valid
            if (!session.isConnected()) {
                logger.warn("Session disconnected, creating new session");
                session = dfcSessionManager.getSession(dfcConfig.getRepositoryName());
            }
        } catch (DfException e) {
            logger.error("Error checking session status", e);
            throw new RuntimeException("Session validation failed", e);
        }

        logger.debug("Session acquired from pool. Available: {}", sessionPool.size());
        return session;
    }

    /**
     * Return session to pool
     */
    public void releaseSession(IDfSession session) {
        if (session != null) {
            try {
                if (session.isConnected()) {
                    sessionPool.offer(session);
                    logger.debug("Session returned to pool. Available: {}", sessionPool.size());
                } else {
                    logger.warn("Attempting to return disconnected session, creating new session");
                    IDfSession newSession = dfcSessionManager.getSession(dfcConfig.getRepositoryName());
                    sessionPool.offer(newSession);
                }
            } catch (DfException e) {
                logger.error("Error returning session to pool", e);
            }
        }
    }

    /**
     * Close all sessions and cleanup
     */
    public void shutdown() {
        logger.info("Shutting down DFC session manager");

        sessionPool.forEach(session -> {
            try {
                if (session != null && session.isConnected()) {
                    dfcSessionManager.release(session);
                }
            } catch (Exception e) {
                logger.warn("Error releasing session during shutdown", e);
            }
        });

        sessionPool.clear();

        logger.info("DFC session manager shutdown complete");
    }

    /**
     * Get DFC session manager
     */
    public IDfSessionManager getDfcSessionManager() {
        return dfcSessionManager;
    }

    /**
     * Get repository name
     */
    public String getRepositoryName() {
        return dfcConfig.getRepositoryName();
    }
}
