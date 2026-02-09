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
     * Imports Digidak records from CSV file to target repository.
     */
    public void importDigidakFromCSV(IDfSession session, String csvFilePath, String targetPath, String exportBaseDir,
            int threadCount, String repoName) {
        logger.info("Starting hierarchical import from CSV: " + csvFilePath);
        logger.info("Target Path: " + targetPath);
        logger.info("Thread Count: " + threadCount);

        try {
            ensureTargetPath(session, targetPath);
        } catch (DfException e) {
             logger.error("Failed to ensure target path: " + targetPath, e);
             return;
        }

        Map<String, List<String>> keywordsMap = loadKeywords(exportBaseDir);

        File csvFile = new File(csvFilePath);
        if (!csvFile.exists()) {
            logger.error("CSV file not found: " + csvFilePath);
            return;
        }

        File tempFile = new File(csvFilePath + ".tmp");

        // Count for final stats
        final AtomicInteger successCounter = new AtomicInteger(0);
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

            // Explicitly find the 'object_name' column index for disk folder lookup
            int originalObjectNameIdx = -1;
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equalsIgnoreCase("object_name")) {
                    originalObjectNameIdx = i;
                    break;
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
                    continue; // Skip already successful
                }

                // Process Sequentially on Main Thread
                IDfSession localSession = null;
                try {
                    // We get a fresh session for each Digidak to isolate failures
                    localSession = DocumentumSessionManager.getSession(repoName);
                    String status = "FAILED";

                    try {
                        // Cleanup existing failed Digidak if exists
                        if (objectNameIdx != -1 && objectNameIdx < values.size()) {
                            String digidakName = values.get(objectNameIdx);
                            if (digidakName != null && !digidakName.isEmpty()) {
                                String fullPath = targetPath + (targetPath.endsWith("/") ? "" : "/") + digidakName;
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
                                values.toArray(new String[0]), digidakType, digidakMapping, digidakConstants, targetPath);

                        if (rObjectIdIdx != -1 && rObjectIdIdx < values.size()) {
                            String rObjectId = values.get(rObjectIdIdx);
                            List<String> keywords = keywordsMap.get(rObjectId);
                            if (keywords != null && !keywords.isEmpty()) {
                                if (digidakObj.hasAttr("keywords")) {
                                    digidakObj.removeAll("keywords");
                                    for (String kw : keywords) {
                                        if (digidakObj.isAttrRepeating("keywords")) {
                                            digidakObj.appendString("keywords", kw);
                                        } else {
                                            String existing = digidakObj.getString("keywords");
                                            if (existing == null || existing.isEmpty())
                                                digidakObj.setString("keywords", kw);
                                            else
                                                digidakObj.setString("keywords", existing + "," + kw);
                                        }
                                    }
                                }
                            }
                        }

                        digidakObj.link(targetPath);
                        digidakObj.save();

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
                            importMovementRegisters(localSession, digidakObj, digidakDir);
                            importDocuments(localSession, digidakObj, digidakDir);
                        } else {
                            logger.warn("Digidak directory not found on disk: " + digidakDir.getAbsolutePath());
                        }

                        status = "SUCCESS";
                        successCounter.incrementAndGet();

                    } catch (Exception e) {
                        logger.error("Failed to import Digidak: " + e.getMessage());
                        if (!"FAILED - Cleanup Failed".equals(status)) {
                            status = "FAILED";
                        }
                    }

                    values.set(statusIdx, status);
                    writeLine(writer, values.toArray(new String[0]));
                    writer.flush(); // Flush immediately since we are sequential

                } catch (Exception e) {
                    logger.error("Error processing row", e);
                } finally {
                    if (localSession != null) {
                        safelyReleaseSession(localSession);
                    }
                }
            }

            logger.info("Import completed. Total Digidak records processed: " + processedCounter.get()
                    + ", Successful or Pre-existing: " + successCounter.get());
        } catch (Exception e) {
            logger.error("Error during import operation", e);
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
    }

    /**
     * Writes a CSV line with proper escaping.
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
     * Imports movement registers for a Digidak record.
     */
    private void importMovementRegisters(IDfSession session, IDfSysObject parentDigidak, File digidakDir) throws Exception {
        File movementCsv = new File(digidakDir, "movement_register.csv");
        if (!movementCsv.exists())
            return;

        logger.debug("Importing movements for Digidak: " + parentDigidak.getObjectName());
        try (BufferedReader reader = new BufferedReader(new FileReader(movementCsv))) {
            String headerLine = reader.readLine();
            if (headerLine == null)
                return;

            String[] headers = parseCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line);
                if (values.length < headers.length)
                    continue;

                try {
                    IDfSysObject movementObj = createMappedObject(session, headers, values, movementType,
                            movementMapping, null, null);
                    movementObj.link(parentDigidak.getObjectId().toString());
                    movementObj.save();
                } catch (Exception e) {
                    logger.error("Failed to import movement register: " + e.getMessage());
                    throw e; // Propagate error
                }
            }
        } catch (Exception e) {
            logger.error("Error reading movement_register.csv", e);
            throw e;
        }
    }

    /**
     * Imports documents for a Digidak record.
     */
    private void importDocuments(IDfSession session, IDfSysObject parentDigidak, File digidakDir) throws Exception {
        File docMetadataCsv = new File(digidakDir, "document_metadata.csv");
        if (!docMetadataCsv.exists())
            return;

        logger.debug("Importing documents for Digidak: " + parentDigidak.getObjectName());
        try (BufferedReader reader = new BufferedReader(new FileReader(docMetadataCsv))) {
            String headerLine = reader.readLine();
            if (headerLine == null)
                return;

            String[] headers = parseCsvLine(headerLine);
            int categoryIdx = -1;
            int objectNameIdx = -1;
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].equalsIgnoreCase("category"))
                    categoryIdx = i;
                if (headers[i].equalsIgnoreCase("object_name"))
                    objectNameIdx = i;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line);
                if (values.length < headers.length)
                    continue;

                try {
                    String category = (categoryIdx != -1) ? values[categoryIdx] : "";
                    String objectName = (objectNameIdx != -1) ? values[objectNameIdx] : "";

                    // Keep original name for file lookup
                    String originalObjectName = objectName;

                    // Clean double extension for metadata (e.g. file.doc.doc -> file.doc)
                    if (objectNameIdx != -1) {
                        values[objectNameIdx] = cleanObjectName(objectName);
                    }

                    // All documents use the same type (cms_digidak_document)
                    // No separate notesheet type in Digidak module
                    String typeToUse = documentType;
                    Map<String, String> mappingToUse = documentMapping;

                    IDfSysObject docObj = createMappedObject(session, headers, values, typeToUse, mappingToUse, null, null);

                    // Set content if file exists
                    if (!originalObjectName.isEmpty()) {
                        File contentFile = new File(digidakDir, originalObjectName);

                        // Fallback 1: try removing double extension (e.g. file.doc.doc -> file.doc)
                        if (!contentFile.exists()) {
                            contentFile = new File(digidakDir, cleanObjectName(originalObjectName));
                        }
                        // Fallback 2: try adding .docx
                        if (!contentFile.exists()) {
                            contentFile = new File(digidakDir, originalObjectName + ".docx");
                        }
                        // Fallback 3: try duplicating the extension
                        if (!contentFile.exists() && originalObjectName.contains(".")) {
                            String ext = originalObjectName.substring(originalObjectName.lastIndexOf('.'));
                            contentFile = new File(digidakDir, originalObjectName + ext);
                        }

                        if (contentFile.exists()) {
                            String fmt = detectFormat(session, contentFile.getName());
                            if (!"unknown".equals(fmt)) {
                                docObj.setContentType(fmt);
                            }
                            logger.info("Processing document file: " + contentFile.getAbsolutePath());

                            // Create final reference for lambda
                            final File finalContentFile = contentFile;

                            // Timeout wrapper for setFile
                            java.util.concurrent.Future<?> future = fileSetExecutor.submit(() -> {
                                try {
                                    docObj.setFile(finalContentFile.getAbsolutePath());
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });

                            try {
                                future.get(this.fileTimeoutMinutes, TimeUnit.MINUTES);
                            } catch (java.util.concurrent.TimeoutException te) {
                                future.cancel(true);
                                logger.error("TIMEOUT setting file content: " + contentFile.getAbsolutePath());
                                throw new Exception("Timeout setting file content");
                            }
                        } else {
                            logger.warn("Content file not found: " + contentFile.getAbsolutePath());
                        }

                    }

                    docObj.link(parentDigidak.getObjectId().toString());

                    // Save with timeout
                    java.util.concurrent.Future<?> saveFuture = fileSetExecutor.submit(() -> {
                        try {
                            docObj.save();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

                    try {
                        saveFuture.get(this.fileTimeoutMinutes, TimeUnit.MINUTES);
                    } catch (java.util.concurrent.TimeoutException te) {
                        saveFuture.cancel(true);
                        logger.error("TIMEOUT saving document: "
                                + (originalObjectName.isEmpty() ? docObj.getObjectName() : originalObjectName));
                        throw new Exception("Timeout saving document");
                    }
                } catch (Exception e) {
                    logger.error("Failed to import document: " + e.getMessage());
                    throw e; // Propagate
                }
            }
        } catch (Exception e) {
            logger.error("Error reading document_metadata.csv", e);
            throw e;
        }
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
            // Bypass TBO instantiation by creating as dm_folder first
            obj = (IDfSysObject) session.newObject("dm_folder");
        } else {
            obj = (IDfSysObject) session.newObject(targetType);
        }

        // Apply attributes (first pass)
        populateObjectAttributes(obj, headers, values, mapping, constants);

        if (isTboType) {
            // Link to target path avoids default cabinet issues
            if (targetPath != null && !targetPath.isEmpty()) {
                 obj.link(targetPath);
            }
            // Save as dm_folder to persist standard attributes
            obj.save();
            String objectId = obj.getObjectId().getId();
            logger.info("Created temp dm_folder with ID: " + objectId + ". Converting to " + targetType);

            // Change Type via DQL
            String dql = "CHANGE dm_folder OBJECTS TO \"cms_digidak_folder\" WHERE r_object_id = '" + objectId + "'";
            IDfQuery query = new DfQuery();
            query.setDQL(dql);
            query.execute(session, IDfQuery.DF_EXEC_QUERY);
            
            // Apply custom attributes via DQL to avoid TBO loading (which hangs on getObject)
            updateAttributesViaDQL(session, objectId, headers, values, mapping, constants);
            
            // We return the original object reference (dm_folder) to avoid triggering TBO load by refetching.
            // The ID is correct, which is what matters for child operations.
        }
        
        // Final flag check (for non-TBO types only)
        if (!isTboType && obj.hasAttr("is_migrated")) {
            obj.setBoolean("is_migrated", true);
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

    private void updateAttributesViaDQL(IDfSession session, String objectId, String[] headers, String[] values,
            Map<String, String> mapping, Map<String, String> constants) throws DfException {
        
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE cms_digidak_folder OBJECTS ");
        boolean hasUpdates = false;

        // Mappings
        for (int i = 0; i < headers.length; i++) {
             String key = headers[i];
             String val = values[i];
             String attr = mapping.get(key);
             if (attr != null && val != null && !val.isEmpty()) {
                 // Skip standard attrs already set on dm_folder
                 if (attr.equals("object_name") || attr.equals("r_creation_date") || attr.equals("r_creator_name")) continue;
                 
                 sb.append("SET ").append(attr).append("='").append(val.replace("'", "''")).append("' ");
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
                // Handle boolean attributes without quotes
                if (key.equals("is_migrated")) {
                    sb.append("SET ").append(key).append("=").append(val.equalsIgnoreCase("true") ? "TRUE" : "FALSE").append(" ");
                } else {
                    sb.append("SET ").append(key).append("='").append(val.replace("'", "''")).append("' ");
                }
                hasUpdates = true;
            }
        }
        
        // Manual override for is_migrated logic
        // If not already in constants (it usually is), ensure it is set
        if (!sb.toString().contains("is_migrated") && (mapping == null || !mapping.containsValue("is_migrated"))) {
             sb.append("SET is_migrated=TRUE ");
             hasUpdates = true;
        }

        if (hasUpdates) {
             sb.append("WHERE r_object_id = '").append(objectId).append("'");
             String dql = sb.toString();
             logger.info("Executing DQL UPDATE: " + dql);
             IDfQuery q = new DfQuery();
             q.setDQL(dql);
             q.execute(session, IDfQuery.DF_EXEC_QUERY);
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
            logger.info("Unlinking existing failed Digidak via DQL: " + folderPath + " (" + objectId + ")");
            
            // Calculate parent path
            String parentPath = "/";
            int lastSlash = folderPath.lastIndexOf('/');
            if (lastSlash > 0) {
                parentPath = folderPath.substring(0, lastSlash);
            }
            
            IDfQuery q = new DfQuery();
            String unlink = "UPDATE dm_sysobject OBJECTS UNLINK '" + parentPath + "' WHERE r_object_id = '" + objectId + "'";
            q.setDQL(unlink);
            q.execute(session, IDfQuery.DF_EXEC_QUERY);
            
            // Rename to avoid collision if still in folder (unlikely if unlink works) or system wide uniqueness?
            // Folder names are unique within parent folder. So simple Unlink is enough.
            // But renaming ensures we don't accidentally pick it up again if we search by name globally (we search by path, so fine).
            
            return true;
        } catch (Exception e) {
            logger.warn("Failed to cleanup (unlink) existing Digidak " + folderPath + ": " + e.getMessage());
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
        obj.destroy();
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
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++;
                } else
                    inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(sb.toString());
                sb.setLength(0);
            } else
                sb.append(c);
        }
        values.add(sb.toString());
        return values.toArray(new String[0]);
    }

    /**
     * Loads keywords from CSV file.
     */
    private Map<String, List<String>> loadKeywords(String exportBaseDir) {
        Map<String, List<String>> keywordsMap = new HashMap<>();
        File csvFile = new File(exportBaseDir, "DigidakKeywords_Export.csv");
        if (!csvFile.exists()) {
            logger.warn("DigidakKeywords_Export.csv not found in " + exportBaseDir);
            return keywordsMap;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String headerLine = reader.readLine(); // skip header
            if (headerLine == null)
                return keywordsMap;

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line);
                if (values.length >= 2) {
                    String id = values[0];
                    String keyword = values[1];
                    if (id != null && !id.isEmpty() && keyword != null && !keyword.isEmpty()) {
                        keywordsMap.computeIfAbsent(id, k -> new ArrayList<>()).add(keyword);
                    }
                }
            }
            logger.info("Loaded keywords for " + keywordsMap.size() + " Digidak records.");
        } catch (Exception e) {
            logger.error("Error loading keywords", e);
        }
        return keywordsMap;
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

            String csvFilePath = exportPath + (exportPath.endsWith(File.separator) ? "" : File.separator)
                    + "DigidakSingleRecords_Export.csv";
            importer.importDigidakFromCSV(session, csvFilePath, importPath, exportPath, importThreadCount, repo);

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
