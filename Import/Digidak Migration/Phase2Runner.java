import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.ImportResult;
import com.digidak.migration.repository.RealDocumentRepository;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.repository.RealSessionManager;
import com.digidak.migration.service.AclService;
import com.digidak.migration.service.DocumentImportService;
import com.digidak.migration.service.FolderService;

/**
 * Runs ONLY Phase 2: Document Import
 * Assumes Phase 1 (Folder Structure) has already been completed
 */
public class Phase2Runner {
    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("DigiDak Migration - PHASE 2 ONLY");
        System.out.println("Document Import");
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
            System.out.println("[CONFIG] Thread Pool Size: " + migrationConfig.getThreadPoolSize());
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
            AclService aclService = new AclService(folderRepository, documentRepository);
            FolderService folderService = new FolderService(folderRepository, migrationConfig);
            System.out.println("[OK] Services initialized");
            System.out.println();

            // Load existing folder structure from Phase 1
            System.out.println("[FOLDERS] Loading existing folder structure from Phase 1...");
            folderService.loadExistingFolderStructure();
            System.out.println("[OK] Loaded " + folderService.getAllFolderIds().size() + " folders from repository");
            System.out.println();

            DocumentImportService documentImportService = new DocumentImportService(
                    documentRepository, folderService, aclService, migrationConfig);
            System.out.println("[OK] Document import service initialized");
            System.out.println();

            // ===========================================
            // PHASE 2: Import Documents
            // ===========================================
            System.out.println("===========================================");
            System.out.println("EXECUTING PHASE 2: Importing Documents");
            System.out.println("===========================================");
            System.out.println();

            long phase2Start = System.currentTimeMillis();
            System.out.println("[PHASE 2] Starting document import...");
            System.out.println("[PHASE 2] This may take several minutes depending on document count...");
            System.out.println();

            ImportResult importResult = documentImportService.importAllDocuments();
            long phase2Duration = System.currentTimeMillis() - phase2Start;

            System.out.println();
            System.out.println("[PHASE 2] Document import completed!");
            System.out.println();

            // Show Phase 2 results
            System.out.println("===========================================");
            System.out.println("PHASE 2 RESULTS");
            System.out.println("===========================================");
            System.out.println();
            System.out.println("[SUMMARY] Total Documents Processed: " + importResult.getTotalDocuments());
            System.out.println("[SUMMARY] Successful Imports: " + importResult.getSuccessfulImports());
            System.out.println("[SUMMARY] Failed Imports: " + importResult.getFailedImports());
            System.out.println("[SUMMARY] Duration: " + phase2Duration + " ms (" + (phase2Duration / 1000) + " seconds)");
            System.out.println();

            // Calculate success rate
            if (importResult.getTotalDocuments() > 0) {
                double successRate = (importResult.getSuccessfulImports() * 100.0) / importResult.getTotalDocuments();
                System.out.println("[RATE] Success Rate: " + String.format("%.2f", successRate) + "%");
                System.out.println();
            }

            // Show errors if any
            if (!importResult.getErrors().isEmpty()) {
                System.out.println("===========================================");
                System.out.println("ERRORS ENCOUNTERED");
                System.out.println("===========================================");
                System.out.println();
                int errorCount = 0;
                for (String error : importResult.getErrors()) {
                    errorCount++;
                    System.out.println("  " + errorCount + ". " + error);
                    if (errorCount >= 10 && importResult.getErrors().size() > 10) {
                        System.out.println("  ... and " + (importResult.getErrors().size() - 10) + " more errors");
                        break;
                    }
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
            System.out.println("PHASE 2 COMPLETED SUCCESSFULLY!");
            System.out.println("===========================================");
            System.out.println("[TIME] Total Duration: " + totalDuration + " ms (" + (totalDuration / 1000) + " seconds)");
            System.out.println("[STATUS] Documents imported and ready for Phase 3 (Movement Registers)");
            System.out.println("===========================================");

        } catch (Exception e) {
            System.err.println("===========================================");
            System.err.println("PHASE 2 FAILED!");
            System.err.println("===========================================");
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
