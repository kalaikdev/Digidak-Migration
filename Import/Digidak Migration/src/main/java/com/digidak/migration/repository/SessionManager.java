package com.digidak.migration.repository;

import com.digidak.migration.config.DfcConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manages Documentum DFC session pool
 * Note: This is a mock implementation for demonstration purposes
 * In production, this would use actual DFC IDfSessionManager
 */
public class SessionManager {
    private static final Logger logger = LogManager.getLogger(SessionManager.class);

    private DfcConfig dfcConfig;
    private BlockingQueue<MockDfcSession> sessionPool;
    private int poolSize;
    private static SessionManager instance;

    private SessionManager(DfcConfig dfcConfig) throws Exception {
        this.dfcConfig = dfcConfig;
        this.poolSize = dfcConfig.getSessionPoolSize();
        this.sessionPool = new LinkedBlockingQueue<>(poolSize);
        initializePool();
    }

    public static synchronized SessionManager getInstance(DfcConfig dfcConfig) throws Exception {
        if (instance == null) {
            instance = new SessionManager(dfcConfig);
        }
        return instance;
    }

    private void initializePool() throws Exception {
        logger.info("Initializing DFC session pool with size: {}", poolSize);

        for (int i = 0; i < poolSize; i++) {
            MockDfcSession session = new MockDfcSession(
                    dfcConfig.getRepositoryName(),
                    dfcConfig.getUsername()
            );
            sessionPool.offer(session);
        }

        logger.info("DFC session pool initialized successfully");
    }

    /**
     * Get session from pool
     */
    public MockDfcSession getSession() throws InterruptedException {
        MockDfcSession session = sessionPool.poll(30, TimeUnit.SECONDS);
        if (session == null) {
            throw new RuntimeException("Timeout waiting for available session");
        }
        logger.debug("Session acquired from pool. Available: {}", sessionPool.size());
        return session;
    }

    /**
     * Return session to pool
     */
    public void releaseSession(MockDfcSession session) {
        if (session != null) {
            sessionPool.offer(session);
            logger.debug("Session returned to pool. Available: {}", sessionPool.size());
        }
    }

    /**
     * Close all sessions and cleanup
     */
    public void shutdown() {
        logger.info("Shutting down session manager");
        sessionPool.forEach(MockDfcSession::disconnect);
        sessionPool.clear();
    }

    /**
     * Mock DFC Session class for demonstration
     * In production, this would be replaced with actual IDfSession
     */
    public static class MockDfcSession {
        private String repository;
        private String username;
        private boolean connected;

        public MockDfcSession(String repository, String username) {
            this.repository = repository;
            this.username = username;
            this.connected = true;
        }

        public String getRepository() {
            return repository;
        }

        public String getUsername() {
            return username;
        }

        public boolean isConnected() {
            return connected;
        }

        public void disconnect() {
            this.connected = false;
        }

        @Override
        public String toString() {
            return "MockDfcSession{" +
                    "repository='" + repository + '\'' +
                    ", username='" + username + '\'' +
                    ", connected=" + connected +
                    '}';
        }
    }
}
