import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.repository.RealSessionManager;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.service.FolderService;

import java.util.Map;

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

            // Initialize folder repository
            System.out.println("[INIT] Initializing folder repository...");
            RealFolderRepository folderRepository = new RealFolderRepository(sessionManager);
            System.out.println("[OK] Folder repository initialized");
            System.out.println();

            // Initialize folder service
            System.out.println("[INIT] Initializing folder service...");
            FolderService folderService = new FolderService(folderRepository, migrationConfig);
            System.out.println("[OK] Folder service initialized");
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
}
