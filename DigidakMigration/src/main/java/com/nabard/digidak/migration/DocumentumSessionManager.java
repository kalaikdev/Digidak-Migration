package com.nabard.digidak.migration;

import com.documentum.fc.client.DfClient;
import com.documentum.fc.client.IDfClient;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfLoginInfo;
import com.documentum.fc.common.IDfLoginInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages Documentum session lifecycle for Digidak Migration operations.
 * Provides thread-safe session management with proper initialization and cleanup.
 */
public class DocumentumSessionManager {

    private static final Logger logger = LogManager.getLogger(DocumentumSessionManager.class);
    private static IDfSessionManager sessionManager = null;

    /**
     * Initializes the Session Manager with credentials.
     * 
     * @param repoName Repository Name
     * @param userName User Name
     * @param password Password
     * @throws DfException if initialization fails
     */
    public static void initSessionManager(String repoName, String userName, String password) throws DfException {
        if (sessionManager == null) {
            IDfClient client = DfClient.getLocalClient();
            sessionManager = client.newSessionManager();

            IDfLoginInfo loginInfo = new DfLoginInfo();
            loginInfo.setUser(userName);
            loginInfo.setPassword(password);
            
            sessionManager.setIdentity(repoName, loginInfo);
            logger.info("Session Manager initialized for repository: " + repoName);
        }
    }

    /**
     * Gets a new session for the specified repository.
     * 
     * @param repoName The repository name
     * @return IDfSession
     * @throws DfException if unable to get session
     */
    public static IDfSession getSession(String repoName) throws DfException {
        if (sessionManager == null) {
            throw new IllegalStateException("Session Manager is not initialized. Call initSessionManager first.");
        }
        return sessionManager.getSession(repoName);
    }

    /**
     * Releases the session back to the manager.
     * 
     * @param session The session to release
     */
    public static void releaseSession(IDfSession session) {
        if (session != null && sessionManager != null) {
            sessionManager.release(session);
            logger.debug("Session released successfully.");
        }
    }

    // Main method for testing connection
    public static void main(String[] args) {
        Properties prop = new Properties();
        try (InputStream input = DocumentumSessionManager.class.getClassLoader().getResourceAsStream("application.properties")) {

            if (input == null) {
                System.out.println("Sorry, unable to find application.properties");
                return;
            }

            // load a properties file from class path, inside static method
            prop.load(input);

            String repoName = prop.getProperty("dctm.repository");
            String userName = prop.getProperty("dctm.username");
            String password = prop.getProperty("dctm.password");

            // Initialize
            initSessionManager(repoName, userName, password);

            // Get Session
            IDfSession session = getSession(repoName);
            System.out.println("Connected to Docbase: " + session.getDocbaseName());
            System.out.println("Server Version: " + session.getServerVersion());

            // Do work...

            // Release
            releaseSession(session);

        } catch (IOException | DfException e) {
            e.printStackTrace();
        }
    }
}
