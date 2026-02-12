package com.digidak.migration.processor;

import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.ImportResult;
import com.digidak.migration.service.DocumentImportService;
import com.digidak.migration.service.FolderService;
import com.digidak.migration.service.MovementRegisterService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Concurrent processor for parallel document import
 */
public class ConcurrentImportProcessor {
    private static final Logger logger = LogManager.getLogger(ConcurrentImportProcessor.class);

    private MigrationConfig config;
    private DocumentImportService documentImportService;
    private FolderService folderService;
    private MovementRegisterService movementRegisterService;
    private ExecutorService executorService;

    public ConcurrentImportProcessor(MigrationConfig config,
                                      DocumentImportService documentImportService,
                                      FolderService folderService,
                                      MovementRegisterService movementRegisterService) {
        this.config = config;
        this.documentImportService = documentImportService;
        this.folderService = folderService;
        this.movementRegisterService = movementRegisterService;
        this.executorService = Executors.newFixedThreadPool(config.getThreadPoolSize());
    }

    /**
     * Process import concurrently
     */
    public ImportResult processImport() {
        logger.info("Starting concurrent import process with {} threads", config.getThreadPoolSize());

        ImportResult result = new ImportResult();

        try {
            // Phase 1: Setup folder structure (sequential)
            logger.info("Phase 1: Setting up folder structure");
            folderService.setupFolderStructure();
            result.incrementFoldersCreated(); // Increment for each folder created

            // Phase 2: Import documents (concurrent)
            logger.info("Phase 2: Importing documents concurrently");
            ImportResult importResult = documentImportService.importAllDocuments();

            // Merge results
            mergeResults(result, importResult);

            // Phase 3: Create movement registers (sequential)
            logger.info("Phase 3: Creating movement registers");
            movementRegisterService.createAllMovementRegisters(result);

            result.markComplete();
            logger.info("Concurrent import process completed");

        } catch (Exception e) {
            logger.error("Error in concurrent import process", e);
            result.addError("Import process failed: " + e.getMessage());
        } finally {
            shutdown();
        }

        return result;
    }

    /**
     * Process folders concurrently
     */
    public ImportResult processFoldersConcurrently() {
        logger.info("Processing folders concurrently");

        ImportResult result = new ImportResult();
        List<Future<ImportResult>> futures = new ArrayList<>();

        try {
            // Setup folder structure first
            folderService.setupFolderStructure();

            // Get all folders to process
            List<File> foldersToProcess = getAllFolders();

            // Submit tasks for each folder
            for (File folder : foldersToProcess) {
                Future<ImportResult> future = executorService.submit(
                        new FolderImportTask(folder, documentImportService)
                );
                futures.add(future);
            }

            // Wait for all tasks to complete
            for (Future<ImportResult> future : futures) {
                try {
                    ImportResult folderResult = future.get();
                    mergeResults(result, folderResult);
                } catch (ExecutionException e) {
                    logger.error("Folder import task failed", e.getCause());
                    result.addError("Task execution failed: " + e.getCause().getMessage());
                }
            }

            // Create movement registers
            movementRegisterService.createAllMovementRegisters(result);

            result.markComplete();

        } catch (Exception e) {
            logger.error("Error in concurrent folder processing", e);
            result.addError("Concurrent processing failed: " + e.getMessage());
        } finally {
            shutdown();
        }

        return result;
    }

    /**
     * Get all folders to process
     */
    private List<File> getAllFolders() {
        List<File> folders = new ArrayList<>();

        String[] directoryNames = {
                "digidak_single_records",
                "digidak_group_records",
                "digidak_subletter_records"
        };

        for (String dirName : directoryNames) {
            String path = config.getDataExportPath() + "/" + dirName;
            File dir = new File(path);

            if (dir.exists() && dir.isDirectory()) {
                File[] subFolders = dir.listFiles(File::isDirectory);
                if (subFolders != null) {
                    for (File folder : subFolders) {
                        folders.add(folder);
                    }
                }
            }
        }

        logger.info("Found {} folders to process", folders.size());
        return folders;
    }

    /**
     * Merge import results
     */
    private void mergeResults(ImportResult target, ImportResult source) {
        for (int i = 0; i < source.getTotalDocuments(); i++) {
            target.incrementTotal();
        }
        for (int i = 0; i < source.getSuccessfulImports(); i++) {
            target.incrementSuccess();
        }
        for (int i = 0; i < source.getFailedImports(); i++) {
            target.incrementFailed();
        }
        for (int i = 0; i < source.getFoldersCreated(); i++) {
            target.incrementFoldersCreated();
        }
        for (int i = 0; i < source.getMovementRegistersCreated(); i++) {
            target.incrementMovementRegisters();
        }
        source.getErrors().forEach(target::addError);
    }

    /**
     * Shutdown executor service
     */
    public void shutdown() {
        logger.info("Shutting down executor service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Callable task for folder import
     */
    private static class FolderImportTask implements Callable<ImportResult> {
        private File folder;
        private DocumentImportService documentImportService;

        public FolderImportTask(File folder, DocumentImportService documentImportService) {
            this.folder = folder;
            this.documentImportService = documentImportService;
        }

        @Override
        public ImportResult call() {
            ImportResult result = new ImportResult();
            try {
                boolean importDocs = !folder.getParentFile().getName().equals("digidak_subletter_records");
                documentImportService.importFromPath(folder.getAbsolutePath(), result);
            } catch (Exception e) {
                logger.error("Error importing folder: {}", folder.getName(), e);
                result.addError("Folder import failed: " + folder.getName() + " - " + e.getMessage());
            }
            return result;
        }
    }
}
