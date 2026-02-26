import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.ImportResult;
import com.digidak.migration.repository.RealDocumentRepository;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.repository.RealSessionManager;
import com.digidak.migration.service.AclService;
import com.digidak.migration.service.DocumentImportService;
import com.digidak.migration.service.FolderService;
import com.digidak.migration.service.UserLookupService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Runs ONLY Phase 2: Document Import
 * Assumes Phase 1 (Folder Structure) has already been completed
 */
public class Phase2Runner {
    private static final Logger logger = LogManager.getLogger(Phase2Runner.class);

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
        log("DigiDak Migration - PHASE 2 ONLY");
        log("Document Import");
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
            log("[CONFIG] Thread Pool Size: " + migrationConfig.getThreadPoolSize());
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

            DocumentImportService documentImportService = new DocumentImportService(
                    documentRepository, folderService, aclService, migrationConfig);
            log("[OK] Document import service initialized");
            log("");

            // ===========================================
            // PHASE 2: Import Documents
            // ===========================================
            log("===========================================");
            log("EXECUTING PHASE 2: Importing Documents");
            log("===========================================");
            log("");

            long phase2Start = System.currentTimeMillis();
            log("[PHASE 2] Starting document import...");
            log("[PHASE 2] This may take several minutes depending on document count...");
            log("");

            ImportResult importResult = documentImportService.importAllDocuments();
            long phase2Duration = System.currentTimeMillis() - phase2Start;

            log("");
            log("[PHASE 2] Document import completed!");
            log("");

            // Show Phase 2 results
            log("===========================================");
            log("PHASE 2 RESULTS");
            log("===========================================");
            log("");
            log("[SUMMARY] Total Documents Processed: " + importResult.getTotalDocuments());
            log("[SUMMARY] Successful Imports: " + importResult.getSuccessfulImports());
            log("[SUMMARY] Failed Imports: " + importResult.getFailedImports());
            log("[SUMMARY] Duration: " + phase2Duration + " ms (" + (phase2Duration / 1000) + " seconds)");
            log("");

            // Calculate success rate
            if (importResult.getTotalDocuments() > 0) {
                double successRate = (importResult.getSuccessfulImports() * 100.0) / importResult.getTotalDocuments();
                log("[RATE] Success Rate: " + String.format("%.2f", successRate) + "%");
                log("");
            }

            // Show errors if any
            if (!importResult.getErrors().isEmpty()) {
                log("===========================================");
                log("ERRORS ENCOUNTERED");
                log("===========================================");
                log("");
                int errorCount = 0;
                for (String error : importResult.getErrors()) {
                    errorCount++;
                    log("  " + errorCount + ". " + error);
                    if (errorCount >= 10 && importResult.getErrors().size() > 10) {
                        log("  ... and " + (importResult.getErrors().size() - 10) + " more errors");
                        break;
                    }
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
            log("PHASE 2 COMPLETED SUCCESSFULLY!");
            log("===========================================");
            log("[TIME] Total Duration: " + totalDuration + " ms (" + (totalDuration / 1000) + " seconds)");
            log("[STATUS] Documents imported and ready for Phase 3 (Movement Registers)");
            log("===========================================");

        } catch (Exception e) {
            logError("===========================================");
            logError("PHASE 2 FAILED!");
            logError("===========================================");
            logError("[ERROR] " + e.getMessage());
            logger.error("Phase 2 failed", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
