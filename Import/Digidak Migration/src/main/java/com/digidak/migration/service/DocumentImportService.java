package com.digidak.migration.service;

import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.DocumentMetadata;
import com.digidak.migration.model.ImportResult;
import com.digidak.migration.parser.MetadataCsvParser;
import com.digidak.migration.repository.RealDocumentRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Service for importing documents
 */
public class DocumentImportService {
    private static final Logger logger = LogManager.getLogger(DocumentImportService.class);

    private RealDocumentRepository documentRepository;
    private FolderService folderService;
    private AclService aclService;
    private MigrationConfig config;
    private MetadataCsvParser metadataParser;

    public DocumentImportService(RealDocumentRepository documentRepository,
                                  FolderService folderService,
                                  AclService aclService,
                                  MigrationConfig config) {
        this.documentRepository = documentRepository;
        this.folderService = folderService;
        this.aclService = aclService;
        this.config = config;
        this.metadataParser = new MetadataCsvParser();
    }

    /**
     * Import all documents from export directory
     */
    public ImportResult importAllDocuments() {
        logger.info("Starting document import process");
        ImportResult result = new ImportResult();

        try {
            // Import single records
            importFromDirectory("digidak_single_records", result, true);

            // Import group records
            importFromDirectory("digidak_group_records", result, true);

            // Import subletter records (metadata only, no documents)
            importFromDirectory("digidak_subletter_records", result, false);

            result.markComplete();
            logger.info("Document import completed: {}", result);

        } catch (Exception e) {
            logger.error("Error during document import", e);
            result.addError("Import failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Import documents from a specific directory
     */
    private void importFromDirectory(String directoryName, ImportResult result,
                                      boolean importDocuments) throws Exception {
        logger.info("Importing from directory: {} (importDocuments: {})",
                directoryName, importDocuments);

        String basePath = config.getDataExportPath() + "/" + directoryName;
        File baseDir = new File(basePath);

        if (!baseDir.exists() || !baseDir.isDirectory()) {
            logger.warn("Directory not found: {}", basePath);
            return;
        }

        // Process each subfolder
        File[] folders = baseDir.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                try {
                    importFolder(folder, result, importDocuments);
                } catch (Exception e) {
                    logger.error("Error importing folder: {}", folder.getName(), e);
                    result.addError("Folder import failed: " + folder.getName() + " - " + e.getMessage());
                    result.incrementFailed();
                }
            }
        }
    }

    /**
     * Import documents from a single folder
     */
    private void importFolder(File folder, ImportResult result, boolean importDocuments) throws Exception {
        String folderName = folder.getName();
        logger.info("Importing folder: {}", folderName);

        // Get folder ID from folder service
        String folderId = folderService.getFolderIdByName(folderName);
        if (folderId == null) {
            logger.warn("Folder ID not found for: {}", folderName);
            return;
        }

        // Parse metadata CSV
        String metadataPath = folder.getAbsolutePath() + "/document_metadata.csv";
        List<DocumentMetadata> metadataList = metadataParser.parseMetadataFile(metadataPath);

        if (metadataList.isEmpty()) {
            logger.info("No metadata found in folder: {}", folderName);
            return;
        }

        // Import each document
        for (DocumentMetadata metadata : metadataList) {
            result.incrementTotal();

            try {
                if (importDocuments) {
                    importDocument(metadata, folder, folderId);
                    result.incrementSuccess();
                    logger.debug("Document imported: {}", metadata.getObjectName());
                } else {
                    logger.debug("Skipping document import for subletter: {}", metadata.getObjectName());
                }
            } catch (Exception e) {
                logger.error("Error importing document: {}", metadata.getObjectName(), e);
                result.addError("Document import failed: " + metadata.getObjectName() + " - " + e.getMessage());
                result.incrementFailed();
            }
        }

        logger.info("Folder import completed: {} ({} documents)", folderName, metadataList.size());
    }

    /**
     * Import a single document
     */
    private void importDocument(DocumentMetadata metadata, File folder, String folderId) throws Exception {
        // Create document in repository
        String documentId = documentRepository.createDocument(metadata, folderId);

        // Set metadata
        documentRepository.setMetadata(documentId, metadata);

        // Attach content file
        String contentFileName = metadata.getObjectName();
        File contentFile = new File(folder, contentFileName);

        if (contentFile.exists()) {
            documentRepository.setContent(documentId, contentFile);
        } else {
            logger.warn("Content file not found: {}", contentFile.getAbsolutePath());
        }

        // Apply ACL from parent folder
        // Temporarily disabled - ACLs from source system don't exist in target
        // String aclId = aclService.getFolderAcl(folderId);
        // if (aclId != null) {
        //     aclService.applyAclToDocument(documentId, aclId);
        // }
        logger.debug("Skipping ACL application (using default folder ACL)");

        // Save document
        documentRepository.save(documentId);

        logger.debug("Document imported successfully: {}", metadata.getObjectName());
    }

    /**
     * Import documents from specific folder path
     */
    public void importFromPath(String folderPath, ImportResult result) throws Exception {
        File folder = new File(folderPath);
        if (folder.exists() && folder.isDirectory()) {
            importFolder(folder, result, true);
        } else {
            throw new IllegalArgumentException("Invalid folder path: " + folderPath);
        }
    }
}
