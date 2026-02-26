import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.repository.RealSessionManager;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.repository.RealDocumentRepository;
import com.digidak.migration.service.FolderService;
import com.digidak.migration.service.UserLookupService;
import com.digidak.migration.service.AclService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;

/**
 * Runs ONLY Phase 1: Folder Structure Setup
 */
public class Phase1Runner {
    private static final Logger logger = LogManager.getLogger(Phase1Runner.class);

    private static void log(String message) {
        System.out.println(message);
        logger.info(message);
    }

    private static void logError(String message) {
        System.err.println(message);
        logger.error(message);
    }

    public static void main(String[] args) {
        log("===========================================");
        log("DigiDak Migration - PHASE 1 ONLY");
        log("Folder Structure Setup");
        log("===========================================");
        log("");

        long startTime = System.currentTimeMillis();

        try {
            // Load configurations
            log("[CONFIG] Loading configurations...");
            DfcConfig dfcConfig = new DfcConfig();
            MigrationConfig migrationConfig = new MigrationConfig();

            log("[CONFIG] Repository: " + dfcConfig.getRepositoryName());
            log("[CONFIG] Cabinet Name: " + migrationConfig.getCabinetName());
            log("[CONFIG] Data Export Path: " + migrationConfig.getDataExportPath());
            log("");

            // Initialize session manager
            log("[INIT] Initializing session manager...");
            RealSessionManager sessionManager = RealSessionManager.getInstance(dfcConfig);
            log("[OK] Session manager initialized");
            log("");

            // Initialize repositories
            log("[INIT] Initializing repositories...");
            RealFolderRepository folderRepository = new RealFolderRepository(sessionManager);
            RealDocumentRepository documentRepository = new RealDocumentRepository(sessionManager);
            log("[OK] Repositories initialized");
            log("");

            // Initialize services
            log("[INIT] Initializing services...");
            UserLookupService userLookupService = new UserLookupService(sessionManager);
            AclService aclService = new AclService(folderRepository, documentRepository, sessionManager);
            FolderService folderService = new FolderService(folderRepository, migrationConfig,
                                                           userLookupService, aclService, sessionManager);
            log("[OK] Services initialized");
            log("");

            // ===========================================
            // PHASE 1: Setup Folder Structure
            // ===========================================
            log("===========================================");
            log("EXECUTING PHASE 1: Setting Up Folder Structure");
            log("===========================================");
            log("");

            long phase1Start = System.currentTimeMillis();
            log("[PHASE 1] Creating folder structure...");
            folderService.setupFolderStructure();
            long phase1Duration = System.currentTimeMillis() - phase1Start;

            log("[PHASE 1] Folder structure creation completed!");
            log("");

            // Show Phase 1 results
            Map<String, String> folderIds = folderService.getAllFolderIds();
            log("===========================================");
            log("PHASE 1 RESULTS");
            log("===========================================");
            log("");
            log("[SUMMARY] Total Folders Created: " + folderIds.size());
            log("[SUMMARY] Duration: " + phase1Duration + " ms (" + (phase1Duration / 1000) + " seconds)");
            log("");
            log("[FOLDERS] Folder Structure:");
            folderIds.forEach((path, id) ->
                log("  [" + id + "] " + path));
            log("");

            // Print ACL statistics
            printAclStatistics(userLookupService);

            // Cleanup
            log("[CLEANUP] Cleaning up resources...");
            sessionManager.shutdown();
            log("[OK] Resources cleaned up");
            log("");

            // Final Summary
            long totalDuration = System.currentTimeMillis() - startTime;
            log("===========================================");
            log("PHASE 1 COMPLETED SUCCESSFULLY!");
            log("===========================================");
            log("[TIME] Total Duration: " + totalDuration + " ms (" + (totalDuration / 1000) + " seconds)");
            log("[STATUS] Folder structure is ready for Phase 2 (Document Import)");
            log("===========================================");

        } catch (Exception e) {
            logError("===========================================");
            logError("PHASE 1 FAILED!");
            logError("===========================================");
            logError("[ERROR] " + e.getMessage());
            logger.error("Phase 1 failed", e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Print ACL and user resolution statistics
     */
    private static void printAclStatistics(UserLookupService userLookupService) {
        log("");
        log("===========================================");
        log("    ACL APPLICATION STATISTICS");
        log("===========================================");

        Map<String, String> resolvedUsers = userLookupService.getUserLoginCache();
        Set<String> notFoundUsers = userLookupService.getNotFoundUsers();

        log("[ACL] Users successfully resolved: " + resolvedUsers.size());
        log("[ACL] Users not found in Documentum: " + notFoundUsers.size());

        if (!notFoundUsers.isEmpty()) {
            log("");
            log("[WARNING] Users not found (first 20):");
            notFoundUsers.stream().limit(20).forEach(user ->
                log("  - " + user)
            );
        }

        log("===========================================");
        log("");
    }
}
