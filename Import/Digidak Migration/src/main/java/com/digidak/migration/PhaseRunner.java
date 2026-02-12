package com.digidak.migration;

import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.ImportResult;
import com.digidak.migration.repository.RealDocumentRepository;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.repository.RealSessionManager;
import com.digidak.migration.service.AclService;
import com.digidak.migration.service.DocumentImportService;
import com.digidak.migration.service.FolderService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Phase-by-phase runner for DigiDak Migration
 * Allows executing specific phases independently
 */
public class PhaseRunner {
    private static final Logger logger = LogManager.getLogger(PhaseRunner.class);

    public static void main(String[] args) {
        logger.info("===========================================");
        logger.info("DigiDak Migration - Phase Runner");
        logger.info("===========================================");

        long startTime = System.currentTimeMillis();

        try {
            // Load configurations
            logger.info("Loading configurations...");
            DfcConfig dfcConfig = new DfcConfig();
            MigrationConfig migrationConfig = new MigrationConfig();

            logger.info("Repository: {}", dfcConfig.getRepositoryName());
            logger.info("Cabinet Name: {}", migrationConfig.getCabinetName());
            logger.info("Data Export Path: {}", migrationConfig.getDataExportPath());

            // Initialize session manager
            logger.info("Initializing session manager...");
            RealSessionManager sessionManager = RealSessionManager.getInstance(dfcConfig);

            // Initialize repositories
            logger.info("Initializing repositories...");
            RealFolderRepository folderRepository = new RealFolderRepository(sessionManager);
            RealDocumentRepository documentRepository = new RealDocumentRepository(sessionManager);

            // Initialize services
            logger.info("Initializing services...");
            AclService aclService = new AclService(folderRepository, documentRepository);
            FolderService folderService = new FolderService(folderRepository, migrationConfig);
            DocumentImportService documentImportService = new DocumentImportService(
                    documentRepository, folderService, aclService, migrationConfig);

            // ===========================================
            // PHASE 1: Setup Folder Structure
            // ===========================================
            logger.info("\n");
            logger.info("===========================================");
            logger.info("PHASE 1: Setting Up Folder Structure");
            logger.info("===========================================");

            long phase1Start = System.currentTimeMillis();
            folderService.setupFolderStructure();
            long phase1Duration = System.currentTimeMillis() - phase1Start;

            // Show Phase 1 results
            Map<String, String> folderIds = folderService.getAllFolderIds();
            logger.info("\n");
            logger.info("--- PHASE 1 RESULTS ---");
            logger.info("Total Folders Created: {}", folderIds.size());
            logger.info("Duration: {} ms", phase1Duration);
            logger.info("\n");
            logger.info("Folder Structure:");
            folderIds.forEach((path, id) ->
                logger.info("  [{}] {}", id, path));
            logger.info("\n");

            // ===========================================
            // PHASE 2: Import Documents
            // ===========================================
            logger.info("===========================================");
            logger.info("PHASE 2: Importing Documents");
            logger.info("===========================================");

            long phase2Start = System.currentTimeMillis();
            ImportResult importResult = documentImportService.importAllDocuments();
            long phase2Duration = System.currentTimeMillis() - phase2Start;

            // Show Phase 2 results
            logger.info("\n");
            logger.info("--- PHASE 2 RESULTS ---");
            logger.info("Total Documents Processed: {}", importResult.getTotalDocuments());
            logger.info("Successful Imports: {}", importResult.getSuccessfulImports());
            logger.info("Failed Imports: {}", importResult.getFailedImports());
            logger.info("Duration: {} ms", phase2Duration);

            if (!importResult.getErrors().isEmpty()) {
                logger.info("\n");
                logger.info("Errors during import:");
                importResult.getErrors().forEach(error -> logger.error("  - {}", error));
            }
            logger.info("\n");

            // Cleanup
            logger.info("Cleaning up resources...");
            sessionManager.shutdown();

            // Final Summary
            long totalDuration = System.currentTimeMillis() - startTime;
            logger.info("===========================================");
            logger.info("PHASE 1 & 2 COMPLETED SUCCESSFULLY");
            logger.info("===========================================");
            logger.info("Phase 1 Duration: {} ms ({} seconds)", phase1Duration, phase1Duration / 1000);
            logger.info("Phase 2 Duration: {} ms ({} seconds)", phase2Duration, phase2Duration / 1000);
            logger.info("Total Duration: {} ms ({} seconds)", totalDuration, totalDuration / 1000);
            logger.info("===========================================");

        } catch (Exception e) {
            logger.error("Phase execution failed with error", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
