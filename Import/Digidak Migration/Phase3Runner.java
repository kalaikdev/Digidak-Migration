import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.ImportResult;
import com.digidak.migration.repository.RealDocumentRepository;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.repository.RealSessionManager;
import com.digidak.migration.service.FolderService;
import com.digidak.migration.service.MovementRegisterService;

import java.util.Map;

/**
 * Runs ONLY Phase 3: Movement Register Creation
 * Assumes Phase 1 and Phase 2 have already been completed
 */
public class Phase3Runner {
    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("DigiDak Migration - PHASE 3 ONLY");
        System.out.println("Movement Register Creation");
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
            FolderService folderService = new FolderService(folderRepository, migrationConfig);
            System.out.println("[OK] Services initialized");
            System.out.println();

            // Load existing folder structure from Phase 1
            System.out.println("[FOLDERS] Loading existing folder structure from Phase 1...");
            folderService.loadExistingFolderStructure();
            System.out.println("[OK] Loaded " + folderService.getAllFolderIds().size() + " folders from repository");
            System.out.println();

            MovementRegisterService registerService = new MovementRegisterService(
                    documentRepository, folderService, migrationConfig);
            System.out.println("[OK] Movement register service initialized");
            System.out.println();

            // ===========================================
            // PHASE 3: Create Movement Registers
            // ===========================================
            System.out.println("===========================================");
            System.out.println("EXECUTING PHASE 3: Creating Movement Registers");
            System.out.println("===========================================");
            System.out.println();

            long phase3Start = System.currentTimeMillis();
            System.out.println("[PHASE 3] Creating movement registers...");
            System.out.println();

            // Create import result for tracking
            ImportResult result = new ImportResult();

            // Create all movement registers
            registerService.createAllMovementRegisters(result);

            long phase3Duration = System.currentTimeMillis() - phase3Start;

            System.out.println();
            System.out.println("[PHASE 3] Movement register creation completed!");
            System.out.println();

            // Show Phase 3 results
            System.out.println("===========================================");
            System.out.println("PHASE 3 RESULTS");
            System.out.println("===========================================");
            System.out.println();
            System.out.println("[SUMMARY] Movement Registers Created: " + result.getMovementRegistersCreated());
            System.out.println("[SUMMARY] Errors: " + result.getErrors().size());
            System.out.println("[SUMMARY] Duration: " + phase3Duration + " ms (" + (phase3Duration / 1000) + " seconds)");
            System.out.println();

            // Show errors if any
            if (!result.getErrors().isEmpty()) {
                System.out.println("===========================================");
                System.out.println("ERRORS ENCOUNTERED");
                System.out.println("===========================================");
                System.out.println();
                int errorCount = 0;
                for (String error : result.getErrors()) {
                    errorCount++;
                    System.out.println("  " + errorCount + ". " + error);
                }
                System.out.println();
            }

            // Cleanup
            System.out.println("[CLEANUP] Cleaning up resources...");
            sessionManager.shutdown();
            System.out.println("[OK] Resources cleaned up");
            System.out.println();

            // Final Summary
            long totalDuration = System.currentTimeMillis() - startTime;
            System.out.println("===========================================");
            System.out.println("PHASE 3 COMPLETED SUCCESSFULLY!");
            System.out.println("===========================================");
            System.out.println("[TIME] Total Duration: " + totalDuration + " ms (" + (totalDuration / 1000) + " seconds)");
            System.out.println("[STATUS] All movement registers created - Migration complete!");
            System.out.println("===========================================");

        } catch (Exception e) {
            System.err.println("===========================================");
            System.err.println("PHASE 3 FAILED!");
            System.err.println("===========================================");
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
