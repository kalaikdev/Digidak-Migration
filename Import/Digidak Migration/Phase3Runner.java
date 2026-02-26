import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.ImportResult;
import com.digidak.migration.repository.RealDocumentRepository;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.repository.RealSessionManager;
import com.digidak.migration.service.AclService;
import com.digidak.migration.service.FolderService;
import com.digidak.migration.service.MovementRegisterService;
import com.digidak.migration.service.UserLookupService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Runs ONLY Phase 3: Movement Register Creation
 * Assumes Phase 1 and Phase 2 have already been completed
 */
public class Phase3Runner {
    private static final Logger logger = LogManager.getLogger(Phase3Runner.class);

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
        log("DigiDak Migration - PHASE 3 ONLY");
        log("Movement Register Creation");
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
                                                           userLookupService, aclService);
            log("[OK] Services initialized");
            log("");

            // Load existing folder structure from Phase 1
            log("[FOLDERS] Loading existing folder structure from Phase 1...");
            folderService.loadExistingFolderStructure();
            log("[OK] Loaded " + folderService.getAllFolderIds().size() + " folders from repository");
            log("");

            MovementRegisterService registerService = new MovementRegisterService(
                    documentRepository, folderService, migrationConfig);
            log("[OK] Movement register service initialized");
            log("");

            // ===========================================
            // PHASE 3: Create Movement Registers
            // ===========================================
            log("===========================================");
            log("EXECUTING PHASE 3: Creating Movement Registers");
            log("===========================================");
            log("");

            long phase3Start = System.currentTimeMillis();
            log("[PHASE 3] Creating movement registers...");
            log("");

            // Create import result for tracking
            ImportResult result = new ImportResult();

            // Create all movement registers
            registerService.createAllMovementRegisters(result);

            long phase3Duration = System.currentTimeMillis() - phase3Start;

            log("");
            log("[PHASE 3] Movement register creation completed!");
            log("");

            // Show Phase 3 results
            log("===========================================");
            log("PHASE 3 RESULTS");
            log("===========================================");
            log("");
            log("[SUMMARY] Movement Registers Created: " + result.getMovementRegistersCreated());
            log("[SUMMARY] Errors: " + result.getErrors().size());
            log("[SUMMARY] Duration: " + phase3Duration + " ms (" + (phase3Duration / 1000) + " seconds)");
            log("");

            // Show errors if any
            if (!result.getErrors().isEmpty()) {
                log("===========================================");
                log("ERRORS ENCOUNTERED");
                log("===========================================");
                log("");
                int errorCount = 0;
                for (String error : result.getErrors()) {
                    errorCount++;
                    log("  " + errorCount + ". " + error);
                }
                log("");
            }

            // Cleanup
            log("[CLEANUP] Cleaning up resources...");
            sessionManager.shutdown();
            log("[OK] Resources cleaned up");
            log("");

            // Final Summary
            long totalDuration = System.currentTimeMillis() - startTime;
            log("===========================================");
            log("PHASE 3 COMPLETED SUCCESSFULLY!");
            log("===========================================");
            log("[TIME] Total Duration: " + totalDuration + " ms (" + (totalDuration / 1000) + " seconds)");
            log("[STATUS] All movement registers created - Migration complete!");
            log("===========================================");

        } catch (Exception e) {
            logError("===========================================");
            logError("PHASE 3 FAILED!");
            logError("===========================================");
            logError("[ERROR] " + e.getMessage());
            logger.error("Phase 3 failed", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
