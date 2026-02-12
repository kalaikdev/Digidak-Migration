package com.digidak.migration;

import com.digidak.migration.config.DfcConfig;
import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.ImportResult;
import com.digidak.migration.processor.ConcurrentImportProcessor;
import com.digidak.migration.repository.RealDocumentRepository;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.repository.RealSessionManager;
import com.digidak.migration.service.AclService;
import com.digidak.migration.service.DocumentImportService;
import com.digidak.migration.service.FolderService;
import com.digidak.migration.service.MovementRegisterService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Main application class for DigiDak Migration
 */
public class DigidakMigrationApp {
    private static final Logger logger = LogManager.getLogger(DigidakMigrationApp.class);

    public static void main(String[] args) {
        logger.info("===========================================");
        logger.info("DigiDak Migration Application Starting");
        logger.info("===========================================");

        long startTime = System.currentTimeMillis();

        try {
            // Load configurations
            logger.info("Loading configurations...");
            DfcConfig dfcConfig = new DfcConfig();
            MigrationConfig migrationConfig = new MigrationConfig();

            logger.info("Repository: {}", dfcConfig.getRepositoryName());
            logger.info("Thread Pool Size: {}", migrationConfig.getThreadPoolSize());
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
            MovementRegisterService movementRegisterService = new MovementRegisterService(
                    documentRepository, folderService, migrationConfig);

            // Initialize concurrent processor
            logger.info("Initializing concurrent processor...");
            ConcurrentImportProcessor processor = new ConcurrentImportProcessor(
                    migrationConfig, documentImportService, folderService, movementRegisterService);

            // Execute migration
            logger.info("===========================================");
            logger.info("Starting Migration Process");
            logger.info("===========================================");

            ImportResult result = processor.processImport();

            // Print results
            printResults(result);

            // Generate report
            generateReport(result);

            // Cleanup
            logger.info("Cleaning up resources...");
            sessionManager.shutdown();

            long duration = System.currentTimeMillis() - startTime;
            logger.info("===========================================");
            logger.info("Migration Completed Successfully");
            logger.info("Total Duration: {} ms ({} seconds)", duration, duration / 1000);
            logger.info("===========================================");

        } catch (Exception e) {
            logger.error("Migration failed with error", e);
            System.exit(1);
        }
    }

    /**
     * Print import results
     */
    private static void printResults(ImportResult result) {
        logger.info("\n");
        logger.info("===========================================");
        logger.info("            MIGRATION RESULTS");
        logger.info("===========================================");
        logger.info("Total Documents Processed: {}", result.getTotalDocuments());
        logger.info("Successful Imports: {}", result.getSuccessfulImports());
        logger.info("Failed Imports: {}", result.getFailedImports());
        logger.info("Folders Created: {}", result.getFoldersCreated());
        logger.info("Movement Registers Created: {}", result.getMovementRegistersCreated());
        logger.info("Duration: {} ms", result.getDurationMillis());

        if (!result.getErrors().isEmpty()) {
            logger.info("\n");
            logger.info("Errors ({}):", result.getErrors().size());
            result.getErrors().forEach(error -> logger.error("  - {}", error));
        }

        logger.info("===========================================\n");
    }

    /**
     * Generate HTML report
     */
    private static void generateReport(ImportResult result) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String reportPath = "migration_report_" + timestamp + ".html";

        try (PrintWriter writer = new PrintWriter(new FileWriter(reportPath))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("<title>DigiDak Migration Report</title>");
            writer.println("<style>");
            writer.println("body { font-family: Arial, sans-serif; margin: 20px; }");
            writer.println("h1 { color: #333; }");
            writer.println("table { border-collapse: collapse; width: 100%; margin-top: 20px; }");
            writer.println("th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }");
            writer.println("th { background-color: #4CAF50; color: white; }");
            writer.println(".success { color: green; }");
            writer.println(".error { color: red; }");
            writer.println(".info { color: blue; }");
            writer.println("</style>");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("<h1>DigiDak Migration Report</h1>");
            writer.println("<p>Generated: " + new Date() + "</p>");

            writer.println("<h2>Summary</h2>");
            writer.println("<table>");
            writer.println("<tr><th>Metric</th><th>Value</th></tr>");
            writer.println("<tr><td>Total Documents</td><td>" + result.getTotalDocuments() + "</td></tr>");
            writer.println("<tr><td class='success'>Successful Imports</td><td class='success'>" +
                    result.getSuccessfulImports() + "</td></tr>");
            writer.println("<tr><td class='error'>Failed Imports</td><td class='error'>" +
                    result.getFailedImports() + "</td></tr>");
            writer.println("<tr><td>Folders Created</td><td>" + result.getFoldersCreated() + "</td></tr>");
            writer.println("<tr><td>Movement Registers</td><td>" + result.getMovementRegistersCreated() + "</td></tr>");
            writer.println("<tr><td>Duration (ms)</td><td>" + result.getDurationMillis() + "</td></tr>");
            writer.println("</table>");

            if (!result.getErrors().isEmpty()) {
                writer.println("<h2>Errors</h2>");
                writer.println("<ul>");
                result.getErrors().forEach(error -> writer.println("<li class='error'>" + error + "</li>"));
                writer.println("</ul>");
            }

            writer.println("</body>");
            writer.println("</html>");

            logger.info("Report generated: {}", reportPath);

        } catch (IOException e) {
            logger.error("Error generating report", e);
        }
    }
}
