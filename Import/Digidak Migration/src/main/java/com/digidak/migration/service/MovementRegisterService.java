package com.digidak.migration.service;

import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.ImportResult;
import com.digidak.migration.repository.RealDocumentRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;

/**
 * Service for creating movement registers
 * Movement registers track document movements and are created for single records and subletters
 */
public class MovementRegisterService {
    private static final Logger logger = LogManager.getLogger(MovementRegisterService.class);

    private RealDocumentRepository documentRepository;
    private FolderService folderService;
    private MigrationConfig config;

    public MovementRegisterService(RealDocumentRepository documentRepository,
                                    FolderService folderService,
                                    MigrationConfig config) {
        this.documentRepository = documentRepository;
        this.folderService = folderService;
        this.config = config;
    }

    /**
     * Create all movement registers
     */
    public void createAllMovementRegisters(ImportResult result) throws Exception {
        logger.info("Creating movement registers");

        // Create for single records
        createMovementRegistersForDirectory("digidak_single_records", result);

        // Create for subletter records
        createMovementRegistersForDirectory("digidak_subletter_records", result);

        logger.info("Movement register creation completed");
    }

    /**
     * Create movement registers for a specific directory
     */
    private void createMovementRegistersForDirectory(String directoryName, ImportResult result) throws Exception {
        logger.info("Creating movement registers for: {}", directoryName);

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
                    createMovementRegister(folder.getName(), result);
                } catch (Exception e) {
                    logger.error("Error creating movement register for: {}", folder.getName(), e);
                    result.addError("Movement register creation failed: " + folder.getName() +
                            " - " + e.getMessage());
                }
            }
        }
    }

    /**
     * Create movement registers for a folder by reading the movement_register.csv file
     * Creates one register document for each row in the CSV
     */
    private void createMovementRegister(String folderName, ImportResult result) throws Exception {
        logger.info("Creating movement registers for folder: {}", folderName);

        // Get folder ID - try direct lookup first
        String folderId = folderService.getFolderIdByName(folderName);

        // If not found, it might be a subletter folder - try looking under group folders
        if (folderId == null) {
            folderId = findSubletterFolderId(folderName);
        }

        if (folderId == null) {
            logger.warn("Folder ID not found for: {}", folderName);
            return;
        }

        // Read movement_register.csv file
        String csvPath = getMovementRegisterCsvPath(folderName);
        File csvFile = new File(csvPath);

        if (!csvFile.exists()) {
            logger.warn("Movement register CSV not found: {}", csvPath);
            return;
        }

        // Parse CSV and create one document per row
        int registerCount = 0;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(csvFile))) {
            com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);

            // Read header
            String[] headers = csvReader.readNext();
            if (headers == null) {
                csvReader.close();
                logger.warn("Empty movement register CSV: {}", csvPath);
                return;
            }

            // Find column indices per requirements
            int objectIdIndex = findColumnIndex(headers, "r_object_id");
            int objectNameIndex = findColumnIndex(headers, "object_name");
            int modifiedFromIndex = findColumnIndex(headers, "modified_from");
            int subjectIndex = findColumnIndex(headers, "letter_subject");
            int statusIndex = findColumnIndex(headers, "status");
            int categoryIndex = findColumnIndex(headers, "letter_category");
            int completionDateIndex = findColumnIndex(headers, "completion_date");
            int rCreatorNameIndex = findColumnIndex(headers, "r_creator_name");
            int letterNumberIndex = findColumnIndex(headers, "letter_number");

            // Read data rows and create a document for each
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                if (values.length > 0) {
                    com.digidak.migration.model.DocumentMetadata registerMetadata =
                            new com.digidak.migration.model.DocumentMetadata();

                    // Set basic metadata
                    String objectId = getColumnValue(values, objectIdIndex);
                    String objectName = getColumnValue(values, objectNameIndex);

                    registerMetadata.setObjectName(objectName != null ? objectName : "Movement_Register_" + folderName);
                    registerMetadata.setrObjectType("cms_digidak_movement_re");
                    registerMetadata.setiFolderId(folderId);

                    // Set custom attributes from CSV per updated requirements
                    // status -> status
                    if (statusIndex >= 0 && statusIndex < values.length) {
                        registerMetadata.addCustomAttribute("status", values[statusIndex]);
                    }
                    // letter_subject -> letter_subject
                    if (subjectIndex >= 0 && subjectIndex < values.length) {
                        registerMetadata.addCustomAttribute("letter_subject", values[subjectIndex]);
                    }
                    // completion_date -> completed_date
                    if (completionDateIndex >= 0 && completionDateIndex < values.length) {
                        registerMetadata.addCustomAttribute("completed_date", values[completionDateIndex]);
                    }
                    // completion_date -> received_date
                    if (completionDateIndex >= 0 && completionDateIndex < values.length) {
                        registerMetadata.addCustomAttribute("received_date", values[completionDateIndex]);
                    }
                    // modified_from -> performer
                    if (modifiedFromIndex >= 0 && modifiedFromIndex < values.length) {
                        registerMetadata.addCustomAttribute("performer", values[modifiedFromIndex]);
                    }
                    // modified_from -> owner_name
                    if (modifiedFromIndex >= 0 && modifiedFromIndex < values.length) {
                        registerMetadata.addCustomAttribute("owner_name", values[modifiedFromIndex]);
                    }
                    // letter_category -> type_category
                    if (categoryIndex >= 0 && categoryIndex < values.length) {
                        registerMetadata.addCustomAttribute("type_category", values[categoryIndex]);
                    }
                    // r_creator_name -> r_creator_name
                    if (rCreatorNameIndex >= 0 && rCreatorNameIndex < values.length) {
                        registerMetadata.addCustomAttribute("r_creator_name", values[rCreatorNameIndex]);
                    }
                    // r_object_id -> migrated_id
                    if (objectId != null && !objectId.isEmpty()) {
                        registerMetadata.addCustomAttribute("migrated_id", objectId);
                    }
                    // Always set is_migrated = true
                    registerMetadata.addCustomAttribute("is_migrated", true);

                    // Keep letter_number for reference
                    if (letterNumberIndex >= 0 && letterNumberIndex < values.length) {
                        registerMetadata.addCustomAttribute("letter_number", values[letterNumberIndex]);
                    }

                    // Create in repository
                    String registerId = documentRepository.createDocument(registerMetadata, folderId);
                    documentRepository.setMetadata(registerId, registerMetadata);

                    // Set repeating assigned_user attribute from repeating_send_to.csv
                    if (objectId != null && !objectId.isEmpty()) {
                        setRepeatingAssignedUsers(registerId, objectId); // objectId is set as migrated_id
                    }

                    documentRepository.save(registerId, registerMetadata);

                    registerCount++;
                    result.incrementMovementRegisters();
                    logger.debug("Movement register created: {} for folder: {}", objectName, folderName);
                }
            }
            csvReader.close();
        } catch (Exception e) {
            logger.error("Error reading movement register CSV for {}: {}", folderName, e.getMessage(), e);
            throw e;
        }

        logger.info("Created {} movement register(s) for folder: {}", registerCount, folderName);
    }

    /**
     * Get the path to the movement_register.csv file for a folder
     */
    private String getMovementRegisterCsvPath(String folderName) {
        // Try single records first
        String path = config.getDataExportPath() + "/digidak_single_records/" + folderName + "/movement_register.csv";
        if (new File(path).exists()) {
            return path;
        }

        // Try subletter records
        path = config.getDataExportPath() + "/digidak_subletter_records/" + folderName + "/movement_register.csv";
        if (new File(path).exists()) {
            return path;
        }

        // Try group records
        path = config.getDataExportPath() + "/digidak_group_records/" + folderName + "/movement_register.csv";
        return path;
    }

    /**
     * Find column index by name
     */
    private int findColumnIndex(String[] headers, String columnName) {
        for (int i = 0; i < headers.length; i++) {
            if (columnName.equals(headers[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get column value safely
     */
    private String getColumnValue(String[] values, int index) {
        if (index >= 0 && index < values.length) {
            return values[index].trim();
        }
        return null;
    }

    /**
     * Create movement register for a specific document
     */
    public String createDocumentMovementRegister(String documentId, String folderId) throws Exception {
        logger.debug("Creating movement register for document: {}", documentId);

        com.digidak.migration.model.DocumentMetadata registerMetadata =
                new com.digidak.migration.model.DocumentMetadata();

        registerMetadata.setObjectName("Movement_Register_Doc_" + documentId);
        registerMetadata.setrObjectType("cms_digidak_movement_re");
        registerMetadata.setiFolderId(folderId);
        registerMetadata.setDocumentType("Document Movement Register");
        registerMetadata.addCustomAttribute("related_document_id", documentId);
        registerMetadata.addCustomAttribute("creation_date", new java.util.Date());

        String registerId = documentRepository.createDocument(registerMetadata, folderId);
        documentRepository.setMetadata(registerId, registerMetadata);
        documentRepository.save(registerId);

        logger.debug("Document movement register created: {}", registerId);
        return registerId;
    }

    /**
     * Find folder ID for subletter folders (nested under group folders)
     */
    private String findSubletterFolderId(String folderName) {
        try {
            // Get parent group from config
            String parentGroup = config.getProperty("subletter." + folderName + ".parent");
            if (parentGroup != null) {
                String cabinetName = config.getCabinetName();
                String folderPath = "/" + cabinetName + "/" + parentGroup + "/" + folderName;
                String folderId = folderService.getFolderIdByPath(folderPath);
                if (folderId != null) {
                    logger.debug("Found subletter folder {} under parent {}", folderName, parentGroup);
                    return folderId;
                }
            }

            // Fallback: search all folder IDs for a path ending with this folder name
            for (java.util.Map.Entry<String, String> entry : folderService.getAllFolderIds().entrySet()) {
                String path = entry.getKey();
                if (path.endsWith("/" + folderName)) {
                    logger.debug("Found folder by path search: {}", path);
                    return entry.getValue();
                }
            }
        } catch (Exception e) {
            logger.warn("Error finding subletter folder {}: {}", folderName, e.getMessage());
        }
        return null;
    }

    /**
     * Set repeating assigned_user attribute from repeating_send_to.csv
     * Per requirements: send_to -> assigned_user (repeating attribute)
     * Matches on migrated_id column (falls back to r_object_id for backward compatibility)
     */
    private void setRepeatingAssignedUsers(String registerId, String migratedId) throws Exception {
        logger.debug("Setting repeating assigned_user for movement register: {} (migrated_id: {})",
                     registerId, migratedId);

        // Read repeating_send_to.csv file
        String csvPath = config.getDataExportPath() + "/repeating_send_to.csv";
        File csvFile = new File(csvPath);

        if (!csvFile.exists()) {
            logger.warn("repeating_send_to.csv not found: {}", csvPath);
            return;
        }

        java.util.List<String> assignedUsers = new java.util.ArrayList<>();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(csvFile))) {
            com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);

            // Read header
            String[] headers = csvReader.readNext();
            if (headers == null) {
                csvReader.close();
                return;
            }

            // Find column indices - check for migrated_id first, fallback to r_object_id
            int migratedIdIndex = findColumnIndex(headers, "migrated_id");
            if (migratedIdIndex < 0) {
                migratedIdIndex = findColumnIndex(headers, "r_object_id");
                logger.debug("Using r_object_id column for matching (migrated_id column not found)");
            } else {
                logger.debug("Using migrated_id column for matching");
            }
            int sendToIndex = findColumnIndex(headers, "send_to");

            if (migratedIdIndex < 0 || sendToIndex < 0) {
                logger.warn("Required columns not found in repeating_send_to.csv (need migrated_id/r_object_id and send_to)");
                csvReader.close();
                return;
            }

            // Read all rows and collect assigned users for this migrated_id
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                if (values.length > Math.max(migratedIdIndex, sendToIndex)) {
                    String rowMigratedId = values[migratedIdIndex].trim();
                    if (migratedId.equals(rowMigratedId)) {
                        String sendTo = values[sendToIndex].trim();
                        if (!sendTo.isEmpty()) {
                            assignedUsers.add(sendTo);
                            logger.debug("Found assigned user for migrated_id {}: {}", migratedId, sendTo);
                        }
                    }
                }
            }
            csvReader.close();
        } catch (Exception e) {
            logger.error("Error reading repeating_send_to.csv: {}", e.getMessage(), e);
            throw e;
        }

        // Set repeating assigned_user attribute if we found any users
        if (!assignedUsers.isEmpty()) {
            documentRepository.setRepeatingAttribute(registerId, "assigned_user", assignedUsers);
            logger.info("Set {} assigned user(s) for movement register with migrated_id {}",
                       assignedUsers.size(), migratedId);
        } else {
            logger.debug("No assigned users found for migrated_id: {}", migratedId);
        }
    }
}
