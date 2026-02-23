import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.repository.RealSessionManager;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.repository.RealDocumentRepository;
import com.digidak.migration.service.FolderService;
import com.digidak.migration.service.UserLookupService;
import com.digidak.migration.service.AclService;

import java.util.Map;
import java.util.Set;

/**
 * Runs ONLY Phase 1: Folder Structure Setup
 */
public class Phase1Runner {
    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("DigiDak Migration - PHASE 1 ONLY");
        System.out.println("Folder Structure Setup");
        System.out.println("===========================================");
        System.out.println();

        long startTime = System.currentTimeMillis();

        try {
            // Load configurations
            System.out.println("[CONFIG] Loading configurations...");
            DfcConfig dfcConfig = new DfcConfig();
            MigrationConfig migrationConfig = new MigrationConfig();

            System.out.println("[CONFIG] Repository: " + dfcConfig.getRepositoryName());
            System.out.println("[CONFIG] Cabinet Name: " + migrationConfig.getCabinetName());
            System.out.println("[CONFIG] Data Export Path: " + migrationConfig.getDataExportPath());
            System.out.println();

            // Initialize session manager
            System.out.println("[INIT] Initializing session manager...");
            RealSessionManager sessionManager = RealSessionManager.getInstance(dfcConfig);
            System.out.println("[OK] Session manager initialized");
            System.out.println();

            // Initialize repositories
            System.out.println("[INIT] Initializing repositories...");
            RealFolderRepository folderRepository = new RealFolderRepository(sessionManager);
            RealDocumentRepository documentRepository = new RealDocumentRepository(sessionManager);
            System.out.println("[OK] Repositories initialized");
            System.out.println();

            // Initialize services
            System.out.println("[INIT] Initializing services...");
            UserLookupService userLookupService = new UserLookupService(sessionManager);
            AclService aclService = new AclService(folderRepository, documentRepository, sessionManager);
            FolderService folderService = new FolderService(folderRepository, migrationConfig,
                                                           userLookupService, aclService, sessionManager);
            System.out.println("[OK] Services initialized");
            System.out.println();

            // ===========================================
            // PHASE 1: Setup Folder Structure
            // ===========================================
            System.out.println("===========================================");
            System.out.println("EXECUTING PHASE 1: Setting Up Folder Structure");
            System.out.println("===========================================");
            System.out.println();

            long phase1Start = System.currentTimeMillis();
            System.out.println("[PHASE 1] Creating folder structure...");
            folderService.setupFolderStructure();
            long phase1Duration = System.currentTimeMillis() - phase1Start;

            System.out.println("[PHASE 1] Folder structure creation completed!");
            System.out.println();

            // Show Phase 1 results
            Map<String, String> folderIds = folderService.getAllFolderIds();
            System.out.println("===========================================");
            System.out.println("PHASE 1 RESULTS");
            System.out.println("===========================================");
            System.out.println();
            System.out.println("[SUMMARY] Total Folders Created: " + folderIds.size());
            System.out.println("[SUMMARY] Duration: " + phase1Duration + " ms (" + (phase1Duration / 1000) + " seconds)");
            System.out.println();
            System.out.println("[FOLDERS] Folder Structure:");
            folderIds.forEach((path, id) ->
                System.out.println("  [" + id + "] " + path));
            System.out.println();

            // Print ACL statistics
            printAclStatistics(userLookupService);

            // Cleanup
            System.out.println("[CLEANUP] Cleaning up resources...");
            sessionManager.shutdown();
            System.out.println("[OK] Resources cleaned up");
            System.out.println();

            // Final Summary
            long totalDuration = System.currentTimeMillis() - startTime;
            System.out.println("===========================================");
            System.out.println("PHASE 1 COMPLETED SUCCESSFULLY!");
            System.out.println("===========================================");
            System.out.println("[TIME] Total Duration: " + totalDuration + " ms (" + (totalDuration / 1000) + " seconds)");
            System.out.println("[STATUS] Folder structure is ready for Phase 2 (Document Import)");
            System.out.println("===========================================");

        } catch (Exception e) {
            System.err.println("===========================================");
            System.err.println("PHASE 1 FAILED!");
            System.err.println("===========================================");
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Print ACL and user resolution statistics
     */
    private static void printAclStatistics(UserLookupService userLookupService) {
        System.out.println();
        System.out.println("===========================================");
        System.out.println("    ACL APPLICATION STATISTICS");
        System.out.println("===========================================");

        Map<String, String> resolvedUsers = userLookupService.getUserLoginCache();
        Set<String> notFoundUsers = userLookupService.getNotFoundUsers();

        System.out.println("[ACL] Users successfully resolved: " + resolvedUsers.size());
        System.out.println("[ACL] Users not found in Documentum: " + notFoundUsers.size());

        if (!notFoundUsers.isEmpty()) {
            System.out.println();
            System.out.println("[WARNING] Users not found (first 20):");
            notFoundUsers.stream().limit(20).forEach(user ->
                System.out.println("  - " + user)
            );
        }

        System.out.println("===========================================");
        System.out.println();
    }
}
