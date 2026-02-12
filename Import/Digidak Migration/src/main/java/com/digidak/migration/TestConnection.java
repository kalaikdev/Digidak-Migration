package com.digidak.migration;

import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.repository.RealSessionManager;
import com.documentum.fc.client.IDfSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test Documentum connection with configured credentials
 */
public class TestConnection {
    private static final Logger logger = LogManager.getLogger(TestConnection.class);

    public static void main(String[] args) {
        logger.info("===========================================");
        logger.info("Documentum Connection Test");
        logger.info("===========================================\n");

        try {
            // Load DFC configuration
            logger.info("Loading DFC configuration...");
            DfcConfig dfcConfig = new DfcConfig();

            // Display configuration
            logger.info("\n--- Configuration Loaded ---");
            logger.info("Repository Name: {}", dfcConfig.getRepositoryName());
            logger.info("Docbroker Host: {}", dfcConfig.getDocbrokerHost());
            logger.info("Docbroker Port: {}", dfcConfig.getDocbrokerPort());
            logger.info("Username: {}", dfcConfig.getUsername());
            logger.info("Password: {}",  dfcConfig.getPassword() != null ? "***configured***" : "NOT CONFIGURED");
            logger.info("Session Pool Size: {}", dfcConfig.getSessionPoolSize());
            logger.info("Data Directory: {}", dfcConfig.getDfcProperties().getProperty("dfc.data.dir"));

            // Validate configuration
            if (dfcConfig.getRepositoryName() == null) {
                logger.error("\n❌ FAILED: Repository name not configured!");
                logger.error("Update dfc.properties with:");
                logger.error("  dfc.repository.name=YOUR_REPOSITORY");
                logger.error("  OR uncomment dfc.globalregistry.repository");
                System.exit(1);
            }

            if (dfcConfig.getUsername() == null) {
                logger.error("\n❌ FAILED: Username not configured!");
                logger.error("Update dfc.properties with:");
                logger.error("  dfc.username=YOUR_USERNAME");
                logger.error("  OR uncomment dfc.globalregistry.username");
                System.exit(1);
            }

            if (dfcConfig.getPassword() == null) {
                logger.error("\n❌ FAILED: Password not configured!");
                logger.error("Update dfc.properties with:");
                logger.error("  dfc.password=YOUR_PASSWORD");
                logger.error("  OR uncomment dfc.globalregistry.password");
                System.exit(1);
            }

            logger.info("\n✅ Configuration validation passed!");

            // Initialize Real DFC Session Manager
            logger.info("\n--- Connecting to Documentum ---");
            logger.info("Initializing DFC client...");
            RealSessionManager sessionManager = RealSessionManager.getInstance(dfcConfig);

            logger.info("\n--- Testing Connection ---");
            IDfSession session = sessionManager.getSession();

            logger.info("\n✅ CONNECTION SUCCESSFUL!");
            logger.info("\n--- Repository Information ---");
            logger.info("Repository Name: {}", session.getDocbaseName());
            logger.info("Repository Version: {}", session.getServerVersion());
            logger.info("Connected User: {}", session.getLoginUserName());
            logger.info("Session ID: {}", session.getSessionId());
            logger.info("Connection State: {}", session.isConnected() ? "CONNECTED" : "DISCONNECTED");

            // Release session
            sessionManager.releaseSession(session);

            // Shutdown
            sessionManager.shutdown();

            logger.info("\n===========================================");
            logger.info("✅ Connection Test SUCCESSFUL!");
            logger.info("===========================================");
            logger.info("\nYou can now run the migration:");
            logger.info("  java -cp \"target/digidak-migration-1.0.0-jar-with-dependencies.jar;libs/*\" \\");
            logger.info("    com.digidak.migration.PhaseRunner");
            logger.info("\nFolders will be created in repository: {}", dfcConfig.getRepositoryName());

        } catch (Exception e) {
            logger.error("\n===========================================");
            logger.error("❌ Connection Test FAILED!");
            logger.error("===========================================");
            logger.error("\nError: {}", e.getMessage());
            logger.error("\nPossible causes:");
            logger.error("  1. Incorrect repository name");
            logger.error("  2. Invalid username/password");
            logger.error("  3. Docbroker not reachable at {}:{}",
                "172.172.20.214", "1489");
            logger.error("  4. Repository not registered with docbroker");
            logger.error("  5. DFC libraries not properly configured");
            logger.error("\nFull error details:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
