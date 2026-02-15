package com.nabard.digidak.migration;

import com.documentum.fc.client.IDfFolder;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSysObject;
import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.common.DfException;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.DfQuery;
import com.documentum.fc.common.DfId;
import com.documentum.fc.common.DfTime;
import com.documentum.fc.common.IDfTime;
import java.text.SimpleDateFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Import Operation for Digidak Records.
 * Imports Digidak metadata, movement registers, and documents to target Documentum repository.
 */
public class DigidakImportOperation {

    private static final Logger logger = LogManager.getLogger(DigidakImportOperation.class);
    // Executor for file operations with timeout
    private static final ExecutorService fileSetExecutor = Executors.newCachedThreadPool();

    // Import Log File Writer
    private BufferedWriter importLogWriter = null;
    private String importLogFilePath = null;

    // Mapping Configurations
    private Map<String, String> digidakMapping = new HashMap<>();
    private Map<String, String> digidakConstants = new HashMap<>();
    private String digidakType;

    private Map<String, String> movementMapping = new HashMap<>();
    private String movementType;

    private Map<String, String> notesheetMapping = new HashMap<>();
    private String notesheetType;

    private Map<String, String> documentMapping = new HashMap<>();
    private String documentType;

    private Map<String, String> formatMapping = new HashMap<>();
    private String dateFormat = "dd/MM/yyyy, h:mm:ss a"; // Default

    private int fileTimeoutMinutes = 5;

    /**
     * Sets all configuration mappings for the import operation.
     */
    public void setConfigurations(String digidakType, Map<String, String> digidakMapping, Map<String, String> digidakConstants,
            String movementType, Map<String, String> movementMapping,
            String notesheetType, Map<String, String> notesheetMapping,
            String documentType, Map<String, String> documentMapping,
            Map<String, String> formatMapping, String dateFormat, int fileTimeoutMinutes) {
        this.digidakType = digidakType;
        this.digidakMapping = digidakMapping;
        this.digidakConstants = digidakConstants;
        this.movementType = movementType;
        this.movementMapping = movementMapping;
        this.notesheetType = notesheetType;
        this.notesheetMapping = notesheetMapping;
        this.documentType = documentType;
        this.documentMapping = documentMapping;
        this.formatMapping = formatMapping;
        if (dateFormat != null && !dateFormat.isEmpty()) {
            this.dateFormat = dateFormat;
        }
        this.fileTimeoutMinutes = fileTimeoutMinutes;
    }

    /**
     * Initializes the import log file with CSV headers.
     * Creates a timestamped log file in the logs/ directory.
     */
    private void initImportLog() {
        try {
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            importLogFilePath = "logs" + File.separator + "import_log_" + timestamp + ".csv";
            importLogWriter = new BufferedWriter(new FileWriter(importLogFilePath));
            // Write CSV header
            importLogWriter.write("Timestamp,Row_Number,Object_Name,R_Object_ID,Status,Error_Message,Movements_Imported,Documents_Imported");
            importLogWriter.newLine();
            importLogWriter.flush();
            logger.info("Import log file created: " + importLogFilePath);
        } catch (IOException e) {
            logger.error("Failed to create import log file", e);
        }
    }

    /**
     * Writes a single record entry to the import log file.
     */
    private void writeImportLog(int rowNumber, String objectName, String rObjectId, 
            String status, String errorMessage, int movementsImported, int documentsImported) {
        if (importLogWriter == null) return;
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String safeObjectName = (objectName != null) ? objectName.replace(",", ";").replace("\"", "'") : "";
            String safeError = (errorMessage != null) ? errorMessage.replace(",", ";").replace("\"", "'").replace("\n", " ").replace("\r", "") : "";
            String safeRObjectId = (rObjectId != null) ? rObjectId : "";
            
            importLogWriter.write(String.format("%s,%d,\"%s\",%s,%s,\"%s\",%d,%d",
                    timestamp, rowNumber, safeObjectName, safeRObjectId, status, safeError, 
                    movementsImported, documentsImported));
            importLogWriter.newLine();
            importLogWriter.flush();
        } catch (IOException e) {
            logger.error("Failed to write to import log", e);
        }
    }

    /**
     * Writes a summary line to the import log file.
     */
    private void writeImportLogSummary(int totalProcessed, int totalSuccess, int totalFailed, long durationMs) {
        if (importLogWriter == null) return;
        try {
            importLogWriter.newLine();
            importLogWriter.write("# ========== IMPORT SUMMARY ==========");
            importLogWriter.newLine();
            importLogWriter.write("# Total Records Processed: " + totalProcessed);
            importLogWriter.newLine();
            importLogWriter.write("# Successful: " + totalSuccess);
            importLogWriter.newLine();
            importLogWriter.write("# Failed: " + totalFailed);
            importLogWriter.newLine();
            long durationSeconds = durationMs / 1000;
            long hours = durationSeconds / 3600;
            long minutes = (durationSeconds % 3600) / 60;
            long seconds = durationSeconds % 60;
            importLogWriter.write("# Duration: " + String.format("%02d:%02d:%02d", hours, minutes, seconds));
            importLogWriter.newLine();
            importLogWriter.write("# =====================================");
            importLogWriter.newLine();
            importLogWriter.flush();
        } catch (IOException e) {
            logger.error("Failed to write import log summary", e);
        }
    }

    /**
     * Closes the import log file writer.
     */
    private void closeImportLog() {
        if (importLogWriter != null) {
            try {
                importLogWriter.close();
                logger.info("Import log file closed: " + importLogFilePath);
            } catch (IOException e) {
                logger.error("Failed to close import log file", e);
            }
        }
    }

    /**
     * Imports Digidak records from CSV file to target repository.
     */
    public void importDigidakFromCSV(IDfSession session, String csvFilePath, String targetPath, String exportBaseDir,
            int threadCount, String repoName) {
        logger.info("Starting hierarchical import from CSV: " + csvFilePath);
        logger.info("Target Path: " + targetPath);
        logger.info("Thread Count: " + threadCount);

        // Initialize import log file
        initImportLog();
        long startTime = System.currentTimeMillis();

        // ensureTargetPath SKIPPED - /Digidak Legacy already exists (confirmed via DQL)
        logger.info("Skipping ensureTargetPath - target path assumed to exist: " + targetPath);
        // try {
        //     ensureTargetPath(session, targetPath);
        // } catch (DfException e) {
        //      logger.error("Failed to ensure target path: " + targetPath, e);
        //      writeImportLog(0, "N/A", "N/A", "FAILED", "Failed to ensure target path: " + e.getMessage(), 0, 0);
        //      closeImportLog();
        //      return;
        // }

        // Load repeating attributes from separate CSV files - SKIPPED for now
        // Map<String, Map<String, List<String>>> keywordsMap = loadRepeatingAttributes(exportBaseDir);
        Map<String, Map<String, List<String>>> keywordsMap = new HashMap<>();

        File csvFile = new File(csvFilePath);
        if (!csvFile.exists()) {
            logger.error("CSV file not found: " + csvFilePath);
            return;
        }

        File tempFile = new File(csvFilePath + ".tmp");

        // Count for final stats
        final AtomicInteger successCounter = new AtomicInteger(0);
        final AtomicInteger failedCounter = new AtomicInteger(0);
        final AtomicInteger processedCounter = new AtomicInteger(0);

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {

            String headerLine = reader.readLine();
            if (headerLine == null)
                return;

            List<String> headers = new ArrayList<>(Arrays.asList(parseCsvLine(headerLine)));
            int rObjectIdIdx = -1;
            int statusIdx = -1;

            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equalsIgnoreCase("r_object_id"))
                    rObjectIdIdx = i;
                if (headers.get(i).equalsIgnoreCase("import_status"))
                    statusIdx = i;
            }

            int objectNameIdx = -1;
            // First check config mapping for object_name to find header
            String objectNameHeader = "object_name";
            for (Map.Entry<String, String> entry : digidakMapping.entrySet()) {
                if ("object_name".equals(entry.getValue())) {
                    objectNameHeader = entry.getKey();
                    break;
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equalsIgnoreCase(objectNameHeader)) {
                    objectNameIdx = i;
                    break;
                }
            }

            // Add import_status column if missing
            if (statusIdx == -1) {
                headers.add("import_status");
                statusIdx = headers.size() - 1;
            }

            writeLine(writer, headers.toArray(new String[0]));

            // Explicitly find the 'object_name' and 'i_folder_id' column indices
            int originalObjectNameIdx = -1;
            int iFolderIdIdx = -1;
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                if (header.equalsIgnoreCase("object_name")) {
                    originalObjectNameIdx = i;
                } else if (header.equalsIgnoreCase("i_folder_id")) {
                    iFolderIdIdx = i;
                }
            }

            String line;
            while ((line = reader.readLine()) != null) {
                processedCounter.incrementAndGet();
                List<String> values = new ArrayList<>(Arrays.asList(parseCsvLine(line)));

                // Pad values if needed
                while (values.size() < headers.size()) {
                    values.add("");
                }

                String currentStatus = values.get(statusIdx);
                if ("SUCCESS".equalsIgnoreCase(currentStatus)) {
                    successCounter.incrementAndGet();
                    writeLine(writer, values.toArray(new String[0]));
                    writeImportLog(processedCounter.get(), 
                            (objectNameIdx != -1 && objectNameIdx < values.size()) ? values.get(objectNameIdx) : "unknown",
                            (rObjectIdIdx != -1 && rObjectIdIdx < values.size()) ? values.get(rObjectIdIdx) : "",
                            "SKIPPED_ALREADY_SUCCESS", "", 0, 0);
                    continue; // Skip already successful
                }

                // Process Sequentially on Main Thread
                IDfSession localSession = null;
                try {
                    // We get a fresh session for each Digidak to isolate failures
                    localSession = DocumentumSessionManager.getSession(repoName);
                    
                    String rowTargetPath = targetPath;
                    if (iFolderIdIdx != -1 && iFolderIdIdx < values.size()) {
                        String pId = values.get(iFolderIdIdx);
                        if (pId != null && !pId.isEmpty()) {
                            try {
                                IDfSysObject parent = (IDfSysObject) localSession.getObject(new DfId(pId));
                                String pPath = parent.getPath(0);
                                if (pPath != null && !pPath.isEmpty()) {
                                    rowTargetPath = pPath;
                                } else {
                                    logger.warn("Parent object " + pId + " has no path. Using default.");
                                }
                            } catch (Exception e) {
                                logger.warn("Parent folder not found with ID: " + pId + ". Using default. Error: " + e.getMessage());
                            }
                        }
                    }

                    String status = "FAILED";
                    String errorMsg = "";
                    int movementsCount = 0;
                    int documentsCount = 0;

                    try {
                        // Cleanup existing failed Digidak if exists
                        if (objectNameIdx != -1 && objectNameIdx < values.size()) {
                            String digidakName = values.get(objectNameIdx);
                            if (digidakName != null && !digidakName.isEmpty()) {
                                String fullPath = rowTargetPath + (rowTargetPath.endsWith("/") ? "" : "/") + digidakName;
                                IDfSysObject existingObj = (IDfSysObject) localSession.getObjectByPath(fullPath);
                                if (existingObj != null) {
                                    if (!cleanupDigidak(localSession, existingObj, fullPath)) {
                                        logger.error("Skipping Digidak import because cleanup failed for: " + digidakName);
                                        status = "FAILED - Cleanup Failed";
                                        throw new Exception("Cleanup Failed");
                                    }
                                }
                            }
                        }

                        IDfSysObject digidakObj = createMappedObject(localSession, headers.toArray(new String[0]),
                                values.toArray(new String[0]), digidakType, digidakMapping, digidakConstants, rowTargetPath);

                        if (rObjectIdIdx != -1 && rObjectIdIdx < values.size()) {
                            String rObjectId = values.get(rObjectIdIdx);
                            Map<String, List<String>> attributes = keywordsMap.get(rObjectId);
                            
                            if (attributes != null) {
                                for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
                                    String attrName = entry.getKey();
                                    List<String> attrValues = entry.getValue();
                                    
                                    // Check if attribute exists on object
                                    if (digidakObj.hasAttr(attrName)) {
                                        // Clear existing values if any (for safety, though object is new)
                                        digidakObj.removeAll(attrName);
                                        
                                        for (String val : attrValues) {
                                            if (digidakObj.isAttrRepeating(attrName)) {
                                                digidakObj.appendString(attrName, val);
                                            } else {
                                                logger.warn("Attribute " + attrName + " is NOT repeating but found in repeating CSVs. Setting single value.");
                                                digidakObj.setString(attrName, val);
                                            }
                                        }
                                        logger.debug("Applied " + attrValues.size() + " values for attribute " + attrName + " on " + digidakObj.getObjectName());
                                    } else {
                                        logger.warn("Object " + digidakObj.getObjectName() + " does not have attribute: " + attrName);
                                    }
                                }
                            }
                        }
                        // For non-TBO types, link and save here. For cms_digidak_folder, already done in createMappedObject.
                        if (!"cms_digidak_folder".equals(digidakType)) {
                            digidakObj.link(rowTargetPath);
                            digidakObj.save();
                        }

                        String digidakFolderName = digidakObj.getObjectName();
                        logger.info("Imported Digidak Folder: " + digidakFolderName);

                        // Hierarchical Import
                        String folderNameOnDisk = (originalObjectNameIdx != -1)
                                ? values.get(originalObjectNameIdx)
                                : digidakFolderName;

                        File digidakDir = new File(
                                exportBaseDir + File.separator + "digidak_single_records" + File.separator + folderNameOnDisk);
                        if (digidakDir.exists() && digidakDir.isDirectory()) {
                            // Skipped: importing only cms_digidak_folder
                            movementsCount = importMovementRegisters(localSession, digidakObj, digidakDir);
                            // documentsCount = importDocuments(localSession, digidakObj, digidakDir);
                        } else {
                            logger.warn("Digidak directory not found on disk: " + digidakDir.getAbsolutePath());
                        }

                        status = "SUCCESS";
                        successCounter.incrementAndGet();

                    } catch (Exception e) {
                        logger.error("Failed to import Digidak: " + e.getMessage());
                        errorMsg = e.getMessage();
                        if (!"FAILED - Cleanup Failed".equals(status)) {
                            status = "FAILED";
                        }
                        failedCounter.incrementAndGet();
                    }

                    values.set(statusIdx, status);
                    writeLine(writer, values.toArray(new String[0]));
                    writer.flush(); // Flush immediately since we are sequential

                    // Write to import log
                    String logObjectName = (objectNameIdx != -1 && objectNameIdx < values.size()) ? values.get(objectNameIdx) : "unknown";
                    String logRObjectId = (rObjectIdIdx != -1 && rObjectIdIdx < values.size()) ? values.get(rObjectIdIdx) : "";
                    writeImportLog(processedCounter.get(), logObjectName, logRObjectId, status, errorMsg, movementsCount, documentsCount);

                } catch (Exception e) {
                    logger.error("Error processing row", e);
                } finally {
                    if (localSession != null) {
                        safelyReleaseSession(localSession);
                    }
                }
            }



            long duration = System.currentTimeMillis() - startTime;
            logger.info("Import completed. Total Digidak records processed: " + processedCounter.get()
                    + ", Successful or Pre-existing: " + successCounter.get()
                    + ", Failed: " + failedCounter.get());
            writeImportLogSummary(processedCounter.get(), successCounter.get(), failedCounter.get(), duration);
        } catch (Exception e) {
            logger.error("Error during import operation", e);
            writeImportLog(0, "N/A", "N/A", "FATAL_ERROR", e.getMessage(), 0, 0);
            closeImportLog();
            return; // Exit if file error
        }

        // Replace original file
        if (csvFile.delete()) {
            if (!tempFile.renameTo(csvFile)) {
                logger.error("Failed to rename temp file to original CSV path.");
            }
        } else {
            logger.error("Failed to delete original CSV file for update.");
        }

        // Close the import log file
        closeImportLog();
    }

    /**
     * Writes a CSV line with proper quoting.
     */
    private void writeLine(BufferedWriter writer, String[] values) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            String val = values[i];
            if (val == null)
                val = "";
            if (i > 0)
                sb.append(",");
            if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
                sb.append("\"");
                sb.append(val.replace("\"", "\"\""));
                sb.append("\"");
            } else {
                sb.append(val);
            }
        }
        sb.append(System.lineSeparator());
        writer.write(sb.toString());
    }

    /**
     * Loads repeating attributes from all repeating_*.csv files in the export directory.
     * Returns Map<ObjectId, Map<AttributeName, List<Value>>>
     */
    private Map<String, Map<String, List<String>>> loadRepeatingAttributes(String exportBaseDir) {
        Map<String, Map<String, List<String>>> repeatingData = new HashMap<>();
        File exportDir = new File(exportBaseDir);
        
        if (!exportDir.exists() || !exportDir.isDirectory()) {
            logger.warn("Export base directory not found: " + exportBaseDir);
            return repeatingData;
        }

        File[] repeatingFiles = exportDir.listFiles((dir, name) -> name.startsWith("repeating_") && name.endsWith(".csv"));
        if (repeatingFiles == null || repeatingFiles.length == 0) {
            logger.info("No repeating attribute files found in " + exportBaseDir);
            return repeatingData;
        }

        for (File csvFile : repeatingFiles) {
            String fileName = csvFile.getName();
            // Extract attribute name: repeating_office_type.csv -> office_type
            String attributeName = fileName.replace("repeating_", "").replace(".csv", "");
            logger.info("Loading repeating attribute: " + attributeName + " from " + fileName);

            try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                String headerLine = reader.readLine(); // skip header: r_object_id, [attribute_name]
                if (headerLine == null) continue;

                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    String[] values = parseCsvLine(line);
                    if (values.length >= 2) {
                        String rObjectId = values[0];
                        String value = values[1];
                        
                        if (rObjectId != null && !rObjectId.isEmpty() && value != null && !value.isEmpty()) {
                            repeatingData
                                .computeIfAbsent(rObjectId, k -> new HashMap<>())
                                .computeIfAbsent(attributeName, k -> new ArrayList<>())
                                .add(value);
                            count++;
                        }
                    }
                }
                logger.info("Loaded " + count + " values for " + attributeName);
            } catch (IOException e) {
                logger.error("Error reading repeating attribute file: " + fileName, e);
            }
        }
        return repeatingData;
    }


    /**
     * Imports movement registers for a Digidak record.
     */
    private int importMovementRegisters(IDfSession session, IDfSysObject parentDigidak, File digidakDir) throws Exception {
        File movementCsv = new File(digidakDir, "movement_register.csv");
        if (!movementCsv.exists()) {
            logger.info("No movement_register.csv found for " + parentDigidak.getObjectName());
            return 0;
        }

        // Create updated CSV with i_folder_id
        File updatedCsv = new File(digidakDir, "movement_register_updated.csv");
        createUpdatedMovementCsv(movementCsv, updatedCsv, parentDigidak.getObjectId().getId());

        logger.info("Importing movement register from: " + updatedCsv.getAbsolutePath());
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(updatedCsv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return 0;

            String[] headers = parseCsvLine(headerLine);
            
             // Ensure mapping for i_folder_id exists
            if (!movementMapping.containsKey("i_folder_id")) {
                movementMapping.put("i_folder_id", "i_folder_id");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line);
                if (values.length < 1) continue;

                try {
                    // Pad values to match headers
                    String[] paddedValues = new String[headers.length];
                    Arrays.fill(paddedValues, "");
                    System.arraycopy(values, 0, paddedValues, 0, Math.min(values.length, headers.length));

                    IDfSysObject movementObj = createMappedObject(session, headers, paddedValues, movementType,
                            movementMapping, null, null);
                    
                    if (movementObj != null) {
                        // movementObj.link(parentDigidak.getObjectId().getId()); // Removed per user request
                        movementObj.save();
                        count++;
                    }
                } catch (Exception e) {
                    logger.error("Failed to import movement register row for " + parentDigidak.getObjectName() + ": " + e.getMessage());
                }
            }
            logger.info("Imported " + count + " movement registers for " + parentDigidak.getObjectName());
        } catch (Exception e) {
            logger.error("Error reading movement_register.csv", e);
            throw e;
        }
        return count;
    }

    private void createUpdatedMovementCsv(File sourceFile, File targetFile, String folderId) {
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(sourceFile))) {
                String line;
                while ((line = br.readLine()) != null) lines.add(line);
            }
            
            if (lines.isEmpty()) return;
            
            String header = lines.get(0);
            boolean hasHeader = header.contains("i_folder_id");
            
            try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new FileWriter(targetFile))) {
                if (hasHeader) {
                    // Already has column, just copy (assuming values are there or we don't touch existing updated files)
                    // But if we are re-generating, maybe we should overwrite values? 
                    // Let's assume we append if not present. If present, we assume it's valid.
                     for (String l : lines) {
                        bw.write(l);
                        bw.newLine();
                    }
                } else {
                    // Add column and values
                    bw.write(header + ",i_folder_id");
                    bw.newLine();
                    for (int i = 1; i < lines.size(); i++) {
                        bw.write(lines.get(i) + "," + folderId);
                        bw.newLine();
                    }
                }
            }
            logger.info("Created movement_register_updated.csv with i_folder_id: " + folderId);
        } catch (Exception e) {
            logger.error("Failed to create updated movement CSV", e);
        }
    }


    /**
     * Imports documents for a Digidak record.
     */
    private int importDocuments(IDfSession session, IDfSysObject parentDigidak, File digidakDir) throws Exception {
        File docMetadataCsv = new File(digidakDir, "document_metadata.csv");
        if (!docMetadataCsv.exists()) {
            logger.info("No document_metadata.csv found for " + parentDigidak.getObjectName());
            return 0;
        }

        logger.info("Importing documents from: " + docMetadataCsv.getAbsolutePath());
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(docMetadataCsv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return 0;

            String[] headers = parseCsvLine(headerLine);
            int categoryIdx = -1;
            int objectNameIdx = -1;
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].equalsIgnoreCase("category")) categoryIdx = i;
                if (headers[i].equalsIgnoreCase("object_name")) objectNameIdx = i;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line);
                if (values.length < 1) continue;
                
                // Pad values
                String[] paddedValues = new String[headers.length];
                Arrays.fill(paddedValues, "");
                System.arraycopy(values, 0, paddedValues, 0, Math.min(values.length, headers.length));
                values = paddedValues;

                try {
                    String objectName = (objectNameIdx != -1) ? values[objectNameIdx] : "";
                    String originalObjectName = objectName;

                    // Clean double extension
                    if (objectNameIdx != -1) {
                        values[objectNameIdx] = cleanObjectName(objectName);
                    }

                    IDfSysObject docObj = createMappedObject(session, headers, values, documentType, documentMapping, null, null);

                    // Set content
                    if (!originalObjectName.isEmpty()) {
                        File contentFile = new File(digidakDir, originalObjectName);
                        
                        // Try various file existence checks
                        if (!contentFile.exists()) contentFile = new File(digidakDir, cleanObjectName(originalObjectName));
                        if (!contentFile.exists()) contentFile = new File(digidakDir, originalObjectName + ".docx");
                        if (!contentFile.exists() && originalObjectName.contains(".")) {
                            String ext = originalObjectName.substring(originalObjectName.lastIndexOf('.'));
                             contentFile = new File(digidakDir, originalObjectName + ext);
                        }

                        if (contentFile.exists()) {
                            String fmt = detectFormat(session, contentFile.getName());
                            if (!"unknown".equals(fmt)) {
                                docObj.setContentType(fmt);
                            }
                            // Important: Set file path for content
                            docObj.setFile(contentFile.getAbsolutePath());
                            logger.info("  -> Attached content: " + contentFile.getName());
                        } else {
                             logger.warn("  -> Content file MISSING for: " + originalObjectName);
                        }
                    }

                    docObj.link(parentDigidak.getObjectId().getId()); // Link to parent folder
                    docObj.save();
                    count++;

                } catch (Exception e) {
                    logger.error("Failed to import document row: " + e.getMessage());
                }
            }
            logger.info("Imported " + count + " documents for " + parentDigidak.getObjectName());
        } catch (Exception e) {
            logger.error("Error reading document_metadata.csv", e);
            throw e;
        }
        return count;
    }

    /**
     * Detects Documentum format from file name.
     */
    private String detectFormat(IDfSession session, String fileName) {
        if (fileName == null || fileName.isEmpty())
            return "unknown";

        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < fileName.length() - 1) {
            ext = fileName.substring(dotIdx + 1).toLowerCase();
        }

        if (ext.isEmpty())
            return "unknown";

        // 1. Check configured map
        if (formatMapping.containsKey(ext)) {
            String fmt = formatMapping.get(ext);
            logger.debug("Format detected locally: " + fmt + " for extension: " + ext);
            return fmt;
        }

        // 2. Lookup in Documentum
        String qual = "dm_format where dos_extension = '" + ext + "'";
        try {
            IDfPersistentObject fmt = session.getObjectByQualification(qual);
            if (fmt != null) {
                String fmtName = fmt.getString("name");
                logger.debug("Format detected via DB: " + fmtName + " for extension: " + ext);
                return fmtName;
            }
        } catch (Exception e) {
            logger.warn("Failed to lookup format for extension: " + ext);
        }

        logger.warn("No format found for extension: " + ext);
        return "unknown";
    }

    /**
     * Creates a Documentum object with mapped attributes.
     */
    private IDfSysObject createMappedObject(IDfSession session, String[] headers, String[] values,
            String targetType, Map<String, String> mapping, Map<String, String> constants, String targetPath) throws DfException {
        
        boolean isTboType = "cms_digidak_folder".equals(targetType);
        IDfSysObject obj;

        if (isTboType) {
            // Create cms_digidak_folder directly via low-level DMCL API to bypass TBO entirely
            String newId = session.apiGet("create", targetType);
            logger.info("Created cms_digidak_folder directly with ID: " + newId);
            
            // Set all attributes via low-level API
            for (int i = 0; i < headers.length; i++) {
                String key = headers[i];
                String val = values[i];
                String attr = mapping.get(key);
                if (attr != null && val != null && !val.isEmpty()) {
                    safeObjectApiSet(session.getObject(new DfId(newId)), attr, val);
                }
            }
            
            // Set constants
            if (constants != null) {
                IDfPersistentObject constObj = session.getObject(new DfId(newId));
                for (Map.Entry<String, String> entry : constants.entrySet()) {
                    String val = entry.getValue();
                    if (entry.getKey().equals("is_migrated")) {
                        val = val.equalsIgnoreCase("true") ? "T" : "F";
                    }
                    safeObjectApiSet(constObj, entry.getKey(), val);
                }
            }
            
            // Set is_migrated if not in constants
            if (constants == null || !constants.containsKey("is_migrated")) {
                safeObjectApiSet(session.getObject(new DfId(newId)), "is_migrated", "T");
            }
            
            // Link to target path
            if (targetPath != null && !targetPath.isEmpty()) {
                session.apiExec("link", newId + "," + targetPath);
            }
            
            // Save via API
            logger.info("Saving cms_digidak_folder via API for object: " + newId);
            session.apiExec("save", newId);
            
            // Fetch and return the object
            obj = (IDfSysObject) session.getObject(new DfId(newId));
        } else {
            obj = (IDfSysObject) session.newObject(targetType);
            // Apply attributes (first pass)
            populateObjectAttributes(obj, headers, values, mapping, constants);
            
            // Final flag check (for non-TBO types only)
            if (obj.hasAttr("is_migrated")) {
                obj.setBoolean("is_migrated", true);
            }
        }

        return obj;
    }

    private void populateObjectAttributes(IDfSysObject obj, String[] headers, String[] values, 
             Map<String, String> mapping, Map<String, String> constants) throws DfException {
        // 1. apply mapping from CSV
        for (int i = 0; i < headers.length; i++) {
            String csvColumn = headers[i];
            String attrValue = values[i];
            String dctmAttr = mapping.get(csvColumn);

            if (dctmAttr != null && attrValue != null && !attrValue.isEmpty()) {
                if (obj.hasAttr(dctmAttr)) {
                    int attrType = obj.getAttrDataType(dctmAttr);
                    // 4 corresponds to IDfAttr.DM_TIME
                    if (attrType == 4) {
                        try {
                            // Use configured date format
                            SimpleDateFormat sdf = new SimpleDateFormat(this.dateFormat);
                            Date date = sdf.parse(attrValue);
                            obj.setTime(dctmAttr, new DfTime(date));
                        } catch (Exception e) {
                            logger.warn("Failed to parse date: " + attrValue + " with format " + this.dateFormat
                                    + " for attribute " + dctmAttr + ". Fallback to setString.");
                            obj.setString(dctmAttr, attrValue);
                        }
                    } else {
                        obj.setString(dctmAttr, attrValue);
                    }
                } 
            }
        }

        // 2. apply constants (overrides CSV if same attribute)
        if (constants != null) {
            for (Map.Entry<String, String> entry : constants.entrySet()) {
                String dqlAttr = entry.getKey();
                String constValue = entry.getValue();
                if (obj.hasAttr(dqlAttr)) {
                    obj.setString(dqlAttr, constValue);
                }
            }
        }
    }

    /**
     * Updates custom attributes on a cms_digidak_folder object using low-level DMCL API
     * (set/save verbs) to completely bypass TBO instantiation.
     */
    /**
     * Helper to invoke apiSet on the object via reflection.
     */
    private boolean safeObjectApiSet(IDfPersistentObject obj, String attr, String value) {
        try {
            // Try apiSet(String attr, String value)
            try {
                java.lang.reflect.Method m = obj.getClass().getMethod("apiSet", String.class, String.class);
                return (Boolean) m.invoke(obj, attr, value);
            } catch (NoSuchMethodException e) {
                 // Try apiSet(String verb, String attr, String value)
                 java.lang.reflect.Method m = obj.getClass().getMethod("apiSet", String.class, String.class, String.class);
                 return (Boolean) m.invoke(obj, "set", attr, value);
            }
        } catch (Exception e) {
            logger.error("Failed to invoke object apiSet via reflection", e);
            return false;
        }
    }

    private String getObjectByMigratedId(IDfSession session, String migratedId) {
        String query = "select r_object_id from cms_digidak_folder where migrated_id = '" + migratedId + "'";
        IDfCollection coll = null;
        try {
            IDfQuery q = new DfQuery();
            q.setDQL(query);
            coll = q.execute(session, IDfQuery.DF_READ_QUERY);
            if (coll.next()) {
                return coll.getString("r_object_id");
            }
        } catch (Exception e) {
            logger.error("Error finding parent folder for migrated_id: " + migratedId, e);
        } finally {
            if (coll != null) {
                try { coll.close(); } catch (Exception e) {}
            }
        }
        return null;
    }
    
    private void updateAttributesViaApi(IDfSession session, String objectId, String[] headers, String[] values,
            Map<String, String> mapping, Map<String, String> constants) throws DfException {
        
        // Fetch object to call apiSet on it
        IDfPersistentObject obj = session.getObject(new DfId(objectId));
        boolean hasUpdates = false;

        // Mappings
        for (int i = 0; i < headers.length; i++) {
             String key = headers[i];
             String val = values[i];
             String attr = mapping.get(key);
             if (attr != null && val != null && !val.isEmpty()) {
                 // Skip standard attrs already set on dm_folder
                 if (attr.equals("object_name") || attr.equals("r_creation_date") || attr.equals("r_creator_name")) continue;
                 
                 logger.debug("Setting attribute via API: " + attr + " = " + val);
                 safeObjectApiSet(obj, attr, val);
                 hasUpdates = true;
             }
        }

        // Constants
        if (constants != null) {
            for (Map.Entry<String, String> entry : constants.entrySet()) {
                String key = entry.getKey();
                // Skip standard
                if (key.equals("object_name") || key.equals("r_creation_date") || key.equals("r_creator_name")) continue;

                String val = entry.getValue();
                // Handle boolean: T/F for DMCL
                if (key.equals("is_migrated")) {
                    val = val.equalsIgnoreCase("true") ? "T" : "F";
                }
                logger.debug("Setting constant via API: " + key + " = " + val);
                safeObjectApiSet(obj, key, val);
                hasUpdates = true;
            }
        }
        
        // Manual override for is_migrated
        if (constants == null || !constants.containsKey("is_migrated")) {
            safeObjectApiSet(obj, "is_migrated", "T");
            hasUpdates = true;
        }

        if (hasUpdates) {
             logger.info("Saving attributes via API for object: " + objectId);
             session.apiExec("save", objectId);
        }
    }

    /**
     * Ensures target path exists in repository.
     */
    private void ensureTargetPath(IDfSession session, String targetPath) throws DfException {
        if (session.getObjectByPath(targetPath) == null) {
            String[] folders = targetPath.split("/");
            String currentPath = "";
            for (String folderName : folders) {
                if (folderName.isEmpty())
                    continue;
                String parentPath = currentPath.isEmpty() ? "/" : currentPath;
                currentPath += "/" + folderName;
                if (session.getObjectByPath(currentPath) == null) {
                    IDfFolder folder = (IDfFolder) session.newObject("dm_folder");
                    folder.setObjectName(folderName);
                    folder.link(parentPath);
                    folder.save();
                }
            }
        }
    }

    /**
     * Cleans object name by removing duplicate extensions.
     */
    private String cleanObjectName(String objectName) {
        if (objectName == null || objectName.isEmpty())
            return objectName;

        String name = objectName;
        while (true) {
            int lastDot = name.lastIndexOf('.');
            if (lastDot > 0) {
                String extension = name.substring(lastDot);
                if (extension.length() <= 6) {
                    name = name.substring(0, lastDot);
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return name;
    }

    /**
     * Cleans up existing failed Digidak folder.
     */
    private boolean cleanupDigidak(IDfSession session, IDfSysObject digidakObj, String folderPath) {
        try {
            String objectId = digidakObj.getObjectId().getId();
            logger.info("cleaning up (deleting) existing failed Digidak: " + folderPath + " (" + objectId + ")");
            
            // Delete recursively to handle children if any
            deleteRecursively(session, digidakObj);
            
            logger.info("Successfully deleted Digidak: " + folderPath);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to cleanup (delete) existing Digidak " + folderPath + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Recursively deletes an object and its children.
     */
    private void deleteRecursively(IDfSession session, IDfSysObject obj) throws DfException {
        if (obj instanceof IDfFolder) {
            IDfFolder folder = (IDfFolder) obj;
            IDfCollection col = null;
            try {
                col = folder.getContents(null);
                while (col.next()) {
                    String childId = col.getString("r_object_id");
                    IDfSysObject child = (IDfSysObject) session.getObject(new DfId(childId));
                    if (child != null) {
                        deleteRecursively(session, child);
                    }
                }
            } finally {
                if (col != null)
                    col.close();
            }
        }
        
        String objId = obj.getObjectId().getId();
        // Downgrade cms_digidak_folder to dm_folder to avoid TBO hang on destroy
        try {
            if ("cms_digidak_folder".equals(obj.getTypeName())) {
                String dql = "CHANGE cms_digidak_folder OBJECTS TO \"dm_folder\" WHERE r_object_id = '" + objId + "'";
                IDfQuery q = new DfQuery();
                q.setDQL(dql);
                IDfCollection c = null;
                try {
                    c = q.execute(session, IDfQuery.DF_EXEC_QUERY);
                } finally {
                    if (c != null) c.close();
                }
                logger.debug("Downgraded " + objId + " to dm_folder for safe deletion");
            }
        } catch (Exception e) {
            logger.warn("Failed to downgrade type for " + objId + ": " + e.getMessage());
        }

        // Use apiExec destroy to bypass TBO
        session.apiExec("destroy", objId);
    }

    /**
     * Parses a CSV line handling quoted values.
     */
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                // Check if next char is also a quote (escaped quote)
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++; // skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString());
        return values.toArray(new String[0]);
    }

    /**
     * Loads keywords from CSV file.
     */
    private Map<String, List<String>> loadKeywords(String exportBaseDir) {
        // This method is kept for compatibility if needed, but loadRepeatingAttributes is preferred.
        // Returning empty map as we are using loadRepeatingAttributes now.
        return new HashMap<>();
    }

    /**
     * Safely releases a session asynchronously.
     */
    private void safelyReleaseSession(IDfSession session) {
        fileSetExecutor.submit(() -> {
            try {
                DocumentumSessionManager.releaseSession(session);
            } catch (Exception e) {
                logger.warn("Error releasing session asynchronously: " + e.getMessage());
            }
        });
    }

    /**
     * Main entry point for Digidak Import Operation.
     */
    public static void main(String[] args) {
        Properties prop = new Properties();
        try (InputStream input = DigidakImportOperation.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null)
                return;
            prop.load(input);

            String repo = prop.getProperty("dctm.repository").trim();
            String user = prop.getProperty("dctm.username").trim();
            String pass = prop.getProperty("dctm.password").trim();
            String exportPath = prop.getProperty("path.export").trim();
            String importPath = prop.getProperty("import.legacy.digidakpath").trim();

            int importThreadCount = 5;
            String importThreadCountStr = prop.getProperty("import.thread.count");
            if (importThreadCountStr != null && !importThreadCountStr.isEmpty()) {
                try {
                    importThreadCount = Integer.parseInt(importThreadCountStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid import.thread.count. Using default: " + importThreadCount);
                }
            }

            int fileTimeoutMinutes = 5;
            String fileTimeoutStr = prop.getProperty("import.file.timeout.minutes");
            if (fileTimeoutStr != null && !fileTimeoutStr.isEmpty()) {
                try {
                    fileTimeoutMinutes = Integer.parseInt(fileTimeoutStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid import.file.timeout.minutes. Using default: " + fileTimeoutMinutes);
                }
            }

            DigidakImportOperation importer = new DigidakImportOperation();
            importer.setConfigurations(
                    prop.getProperty("import.digidak.object_type", "cms_digidak_folder").trim(),
                    getMapping(prop, "import.digidak.mapping."),
                    getMapping(prop, "import.digidak.constant."),
                    prop.getProperty("import.movementregister.object_type", "cms_digidak_movement_re").trim(),
                    getMapping(prop, "import.movementregister.mapping."),
                    prop.getProperty("import.document.object_type", "cms_digidak_document").trim(), // notesheet type same as document
                    getMapping(prop, "import.document.mapping."), // notesheet mapping same as document
                    prop.getProperty("import.document.object_type", "cms_digidak_document").trim(),
                    getMapping(prop, "import.document.mapping."),
                    getMapping(prop, "format.mapping."),
                    prop.getProperty("import.date.format", "dd/MM/yyyy, h:mm:ss a"),
                    fileTimeoutMinutes);

        // Explicitly disable BOF to avoid TBO loading issues
        try {
            java.util.Properties systemProps = System.getProperties();
            systemProps.setProperty("dfc.bof.enable", "false");
            // Also try DfPreferences if available statically/via config
        } catch (Exception ex) {
            logger.warn("Failed to set BOF system property: " + ex.getMessage());
        }

        DocumentumSessionManager.initSessionManager(repo, user, pass);
            IDfSession session = DocumentumSessionManager.getSession(repo);

            List<String> userCsvFiles = Arrays.asList(
                "DigidakSingleRecords_Export.csv"
                // "DigidakGroupRecords_Export.csv",
                // "DigidakSubletterRecords_Export.csv"
            );

            for (String csvFile : userCsvFiles) {
                String csvFilePath = exportPath + (exportPath.endsWith(File.separator) ? "" : File.separator) + csvFile;
                File f = new File(csvFilePath);
                if (f.exists()) {
                    logger.info("----------------------------------------------------------------");
                    logger.info("Starting Batch Import: " + csvFile);
                    logger.info("----------------------------------------------------------------");
                    importer.importDigidakFromCSV(session, csvFilePath, importPath, exportPath, importThreadCount, repo);
                } else {
                    logger.warn("Skipping " + csvFile + " (File not found in " + exportPath + ")");
                }
            }

            DocumentumSessionManager.releaseSession(session);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Extracts mapping properties with given prefix.
     */
    private static Map<String, String> getMapping(Properties prop, String prefix) {
        Map<String, String> map = new HashMap<>();
        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                map.put(key.substring(prefix.length()), prop.getProperty(key).trim());
            }
        }
        return map;
    }
}
