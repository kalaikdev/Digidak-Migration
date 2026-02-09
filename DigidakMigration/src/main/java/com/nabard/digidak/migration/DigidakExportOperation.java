package com.nabard.digidak.migration;

import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSysObject;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfId;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Export Operation for Digidak Records.
 * Extracts Digidak metadata, movement registers, and documents from legacy Documentum repository.
 */
public class DigidakExportOperation {

    private static final Logger logger = LogManager.getLogger(DigidakExportOperation.class);

    /**
     * Extracts Digidak metadata to CSV file.
     * 
     * @param session Documentum session
     * @param outputFilePath Path to output CSV file
     * @param whereClause Optional WHERE clause for filtering
     * @param repoName Repository name
     * @param threadCount Number of threads for parallel processing
     * @param timeoutHours Timeout in hours
     */
    /**
     * Extracts Single Digidak records (non-bulk, non-group letters) to CSV file.
     * WHERE clause: DATETOSTRING("r_creation_date", 'yyyy')='2024' AND group_letter_id=true AND bulk_letter !='true'
     */
    public void extractSingleRecordsCSV(IDfSession session, String outputFilePath, String repoName,
            int threadCount, int timeoutHours) {
        
        String whereClause = "(r_creation_date >= DATE('05/01/2024','mm/dd/yyyy') AND r_creation_date <= DATE('05/02/2024','mm/dd/yyyy')) AND group_letter_id=false AND bulk_letter !='true'";
        String recordsDir = "digidak_single_records";
        String csvFileName = "DigidakSingleRecords_Export.csv";
        
        logger.info("========== Exporting SINGLE Records ==========");
        extractRecordsInternal(session, outputFilePath, whereClause, repoName, threadCount, timeoutHours, recordsDir, csvFileName, true);
    }

    /**
     * Extracts Group Digidak records to CSV file.
     * WHERE clause: Q1 2024 AND group_letter_id=true
     */
    public void extractGroupRecordsCSV(IDfSession session, String outputFilePath, String repoName,
            int threadCount, int timeoutHours) {
        
        String whereClause = "(r_creation_date >= DATE('05/01/2024','mm/dd/yyyy') AND r_creation_date <= DATE('05/02/2024','mm/dd/yyyy')) AND group_letter_id=true";
        String recordsDir = "digidak_group_records";
        String csvFileName = "DigidakGroupRecords_Export.csv";
        
        logger.info("========== Exporting GROUP Records ==========");
        extractRecordsInternal(session, outputFilePath, whereClause, repoName, threadCount, timeoutHours, recordsDir, csvFileName, true);
    }

    /**
     * Extracts Subletter Digidak records (bulk letters) to CSV file.
     * WHERE clause: Q1 2024 AND group_letter_id=false AND bulk_letter='true'
     * Note: Subletter records only export movement_register.csv (no document metadata)
     */
    public void extractSubletterRecordsCSV(IDfSession session, String outputFilePath, String repoName,
            int threadCount, int timeoutHours) {
        
        String whereClause = "(r_creation_date >= DATE('05/01/2024','mm/dd/yyyy') AND r_creation_date <= DATE('05/02/2024','mm/dd/yyyy')) AND group_letter_id=false AND bulk_letter='true'";
        String recordsDir = "digidak_subletter_records";
        String csvFileName = "DigidakSubletterRecords_Export.csv";
        
        logger.info("========== Exporting SUBLETTER Records (Movement Register Only) ==========");
        extractRecordsInternal(session, outputFilePath, whereClause, repoName, threadCount, timeoutHours, recordsDir, csvFileName, false);
    }

    /**
     * Internal method to extract Digidak metadata to CSV file.
     * 
     * @param session Documentum session
     * @param outputBasePath Base path for output
     * @param whereClause WHERE clause for filtering
     * @param repoName Repository name
     * @param threadCount Number of threads for parallel processing
     * @param timeoutHours Timeout in hours
     * @param recordsDir Directory name for records
     * @param csvFileName CSV file name
     * @param exportDocuments If true, exports document metadata and content; if false, only exports movement register
     */
    private void extractRecordsInternal(IDfSession session, String outputBasePath, String whereClause, String repoName,
            int threadCount, int timeoutHours, String recordsDir, String csvFileName, boolean exportDocuments) {
        
        // DQL Query for Digidak (Letter) records from source schema
        // Source type: edmapp_letter_folder
        String baseDql = "SELECT distinct r_object_id, object_name, subject, r_creator_name, r_creation_date, " +
                "status, priority, uid_number, office_type, mode_of_receipt, state_of_recipient, sent_to, " +
                "office_region, group_letter_id, crds_flag, responded_object_id, language_type, address_of_recipient, " +
                "sensitivity, region, ref_number, src_vertical_users, letter_no, financial_year, received_from, " +
                "sub_type, category_external, subjects, category_type, bulk_letter, file_no, type_mode, ho_ro_te, " +
                "from_dept_ro_te FROM edmapp_letter_folder";

        String dql = baseDql;
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            dql += " WHERE " + whereClause;
        }

        logger.info("Executing Query: " + dql);

        // Determine output file path
        File baseDir = new File(outputBasePath);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        String outputFilePath = new File(baseDir, csvFileName).getAbsolutePath();

        IDfCollection collection = null;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {

            IDfQuery query = new DfQuery();
            query.setDQL(dql);
            collection = query.execute(session, IDfQuery.DF_READ_QUERY);

            // Columns to export from edmapp_letter_folder
            String[] headers = {
                    "r_object_id", "object_name", "subject", "r_creator_name", "r_creation_date",
                    "status", "priority", "uid_number", "office_type", "mode_of_receipt", "state_of_recipient",
                    "sent_to", "office_region", "group_letter_id", "crds_flag", "responded_object_id",
                    "language_type", "address_of_recipient", "sensitivity", "region", "ref_number",
                    "src_vertical_users", "letter_no", "financial_year", "received_from", "sub_type",
                    "category_external", "subjects", "category_type", "bulk_letter", "file_no", "type_mode",
                    "ho_ro_te", "from_dept_ro_te"
            };

            // Write Headers
            List<String> outputHeaders = new ArrayList<>(Arrays.asList(headers));
            outputHeaders.add("export_status");
            outputHeaders.add("error_message");
            writeLine(writer, outputHeaders.toArray(new String[0]));

            // Create directory for records
            File digidakDir = new File(baseDir, recordsDir);
            if (!digidakDir.exists()) {
                digidakDir.mkdirs();
            }

            logger.info("Initializing Thread Pool with size: " + threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            int count = 0;
            logger.info("Starting export of records to " + recordsDir + "...");
            while (collection.next()) {
                final List<String> row = new ArrayList<>();
                String tempObjectId = "";
                String tempObjectName = "";
                String tempUidNumber = "";

                // Extract all data from collection while the cursor is strictly on this row
                for (String header : headers) {
                    String val = "";
                    if (collection.hasAttr(header)) {
                        val = collection.getString(header);
                    }
                    row.add(val);

                    if (header.equals("r_object_id")) {
                        tempObjectId = val;
                    } else if (header.equals("object_name")) {
                        tempObjectName = val;
                    } else if (header.equals("uid_number")) {
                        tempUidNumber = val;
                    }
                }

                // Final variables for the thread
                final String digidakObjectId = tempObjectId;
                final String digidakObjectName = tempObjectName;
                final String digidakUidNumber = tempUidNumber;
                final int currentCount = count + 1;

                executor.submit(() -> {
                    IDfSession localSession = null;
                    try {
                        // Check out a dedicated session for this thread
                        localSession = DocumentumSessionManager.getSession(repoName);

                        String exportStatus = "Success";
                        String exportError = "";

                        // Export Movement Register for this Digidak record
                        if (!digidakObjectId.isEmpty() && !digidakObjectName.isEmpty()) {
                            logger.info("Processing record " + currentCount + ": " + digidakObjectName);
                            try {
                                exportMovementRegister(localSession, digidakDir, digidakObjectId, digidakObjectName, digidakUidNumber);
                                if (exportDocuments) {
                                    exportDocumentMetadata(localSession, digidakDir, digidakObjectId, digidakObjectName);
                                }
                            } catch (Exception e) {
                                exportStatus = "Failed";
                                exportError = e.getMessage();
                                logger.error("  -> Nested export failed (" + digidakObjectName + "): " + e.getMessage());
                            }
                        }

                        row.add(exportStatus);
                        row.add(exportError);

                        // Synchronize writing to the shared CSV file
                        synchronized (writer) {
                            writeLine(writer, row.toArray(new String[0]));
                        }

                    } catch (Exception e) {
                        logger.error("Error processing record row " + currentCount + ": " + e.getMessage(), e);
                    } finally {
                        if (localSession != null) {
                            DocumentumSessionManager.releaseSession(localSession);
                        }
                    }
                });

                count++;
            }

            // Wait for all threads to finish
            executor.shutdown();
            try {
                if (!executor.awaitTermination(timeoutHours, TimeUnit.HOURS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            logger.info("Exported " + count + " rows to " + outputFilePath);

        } catch (DfException | IOException e) {
            logger.error("Error in extractRecordsInternal", e);
        } finally {
            if (collection != null) {
                try {
                    collection.close();
                } catch (DfException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Legacy method - Extracts Digidak metadata to CSV file with custom WHERE clause.
     * 
     * @param session Documentum session
     * @param outputFilePath Path to output CSV file
     * @param whereClause Optional WHERE clause for filtering
     * @param repoName Repository name
     * @param threadCount Number of threads for parallel processing
     * @param timeoutHours Timeout in hours
     */
    public void extractDigidakMetaDataCSV(IDfSession session, String outputFilePath, String whereClause, String repoName,
            int threadCount, int timeoutHours) {
        
        File outputFile = new File(outputFilePath);
        String baseDir = outputFile.getParent();
        if (baseDir == null) baseDir = ".";
        
        extractRecordsInternal(session, baseDir, whereClause, repoName, threadCount, timeoutHours, 
                "digidak_records", "DigidakMetadata_Export.csv", true);
    }

    /**
     * Extracts Digidak keywords to separate CSV file.
     */
    public void extractDigidakKeywordsCSV(IDfSession session, String outputFilePath, String whereClause) {
        String baseDql = "SELECT DISTINCT r_object_id, office_type, response_to_ioms_id, src_vertical_users, cgm_and_assigned_groups FROM edmapp_letter_folder";

        String dql = baseDql;
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            dql += " WHERE " + whereClause;
        }
        dql += " ENABLE (ROW_BASED)";

        logger.info("Executing Keywords Query: " + dql);

        File mainExportDir = new File(outputFilePath);
        if (!mainExportDir.exists()) {
            mainExportDir.mkdirs();
        }
        File keywordsFile = new File(mainExportDir, "DigidakRepeating_Export.csv");

        IDfCollection collection = null;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(keywordsFile))) {

            IDfQuery query = new DfQuery();
            query.setDQL(dql);
            collection = query.execute(session, IDfQuery.DF_READ_QUERY);

            String[] headers = { "r_object_id", "office_type", "response_to_ioms_id", "src_vertical_users", "cgm_and_assigned_groups" };
            writeLine(writer, headers);

            int count = 0;
            while (collection.next()) {
                List<String> row = new ArrayList<>();
                for (String header : headers) {
                    String val = "";
                    if (collection.hasAttr(header)) {
                        val = collection.getString(header);
                    }
                    row.add(val);
                }

                writeLine(writer, row.toArray(new String[0]));
                count++;
            }
            logger.info("Exported " + count + " keyword rows to " + keywordsFile.getAbsolutePath());

        } catch (DfException | IOException e) {
            logger.error("Error in extractDigidakKeywordsCSV", e);
        } finally {
            if (collection != null) {
                try {
                    collection.close();
                } catch (DfException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Exports movement register for a specific Digidak record.
     */
    private void exportMovementRegister(IDfSession session, File digidakDir, String digidakObjectId, String digidakObjectName, String uidNumber) {
        // Sanitize folder name
        String safeName = digidakObjectName.replaceAll("[\\\\/:*?\"<>|]", "_");
        File digidakFolder = new File(digidakDir, safeName);
        if (!digidakFolder.exists()) {
            digidakFolder.mkdirs();
        }

        // DQL for movement register from source: edmapp_letter_movement_reg
        // Use letter_number to link with uid_number from edmapp_letter_folder
        String movementDql = "SELECT r_object_id, object_name, modified_from, letter_subject, acl_name, status, letter_category, " +
                "completion_date, letter_number, send_to " +
                "FROM edmapp_letter_movement_reg " +
                "WHERE letter_number='" + uidNumber + "'";

        File movementFile = new File(digidakFolder, "movement_register.csv");

        IDfCollection collection = null;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(movementFile))) {
            IDfQuery query = new DfQuery();
            query.setDQL(movementDql);
            collection = query.execute(session, IDfQuery.DF_READ_QUERY);

            String[] headers = {
                    "r_object_id", "object_name", "modified_from", "letter_subject", "acl_name", "status", "letter_category",
                    "completion_date", "letter_number", "send_to"
            };

            writeLine(writer, headers);

            while (collection.next()) {
                List<String> row = new ArrayList<>();
                for (String header : headers) {
                    String val = "";
                    if (collection.hasAttr(header)) {
                        val = collection.getString(header);
                    }
                    row.add(val);
                }
                writeLine(writer, row.toArray(new String[0]));
            }

        } catch (Exception e) {
            logger.error("Error exporting movement register for Digidak " + digidakObjectName + ": " + e.getMessage(), e);
        } finally {
            if (collection != null) {
                try {
                    collection.close();
                } catch (DfException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Exports document metadata for a specific Digidak record.
     */
    private void exportDocumentMetadata(IDfSession session, File digidakDir, String digidakObjectId, String digidakObjectName) {
        String safeName = digidakObjectName.replaceAll("[\\\\/:*?\"<>|]", "_");
        File digidakFolder = new File(digidakDir, safeName);
        if (!digidakFolder.exists()) {
            digidakFolder.mkdirs();
        }

        // DQL for documents from source: edmapp_letter_document
        String docDql = "select distinct r_object_id, object_name, r_object_type, i_folder_id, r_folder_path, r_content_size, a_content_type, r_creator_name, r_creation_date, document_type " +
                "from edmapp_letter_document doc, dm_folder fol " +
                "where folder(ID('" + digidakObjectId + "'), descend) AND ANY i_folder_id is not null " +
                "AND doc.i_folder_id=fol.r_object_id AND r_folder_path is not null ENABLE (ROW_BASED)";

        File docFile = new File(digidakFolder, "document_metadata.csv");

        IDfCollection collection = null;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(docFile))) {
            IDfQuery query = new DfQuery();
            query.setDQL(docDql);
            collection = query.execute(session, IDfQuery.DF_READ_QUERY);

            String[] headers = {
                    "r_object_id", "object_name", "r_object_type", "i_folder_id", "r_folder_path", "r_creator_name",
                    "r_creation_date", "document_type"
            };

            writeLine(writer, headers);

            while (collection.next()) {
                List<String> row = new ArrayList<>();

                for (String header : headers) {
                    String val = "";
                    if (collection.hasAttr(header)) {
                        val = collection.getString(header);
                    }
                    row.add(val);
                }
                writeLine(writer, row.toArray(new String[0]));

                // Download Content
                try {
                    double contentSize = 0;
                    if (collection.hasAttr("r_content_size")) {
                        contentSize = collection.getDouble("r_content_size");
                    }

                    if (contentSize > 0) {
                        String objectId = collection.getString("r_object_id");
                        String objectName = collection.getString("object_name");

                        IDfSysObject sysObj = (IDfSysObject) session.getObject(new DfId(objectId));
                        String ext = sysObj.getFormat().getDOSExtension();

                        // Sanitize filename
                        String safeFileName = objectName.replaceAll("[\\\\/:*?\"<>|]", "_");
                        if (ext != null && !ext.isEmpty()) {
                            safeFileName += "." + ext;
                        }

                        File destFile = new File(digidakFolder, safeFileName);
                        if (destFile.exists()) {
                            // Avoid overwrite with simple counter or ID
                            safeFileName = objectName.replaceAll("[\\\\/:*?\"<>|]", "_") + "_" + objectId + "." + ext;
                            destFile = new File(digidakFolder, safeFileName);
                        }

                        // DFC getFile is usually the most efficient way to pull content
                        sysObj.getFile(destFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    logger.error("Error downloading document content: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error exporting document metadata for Digidak " + digidakObjectName + ": " + e.getMessage(), e);
        } finally {
            if (collection != null) {
                try {
                    collection.close();
                } catch (DfException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Writes a CSV line with proper escaping.
     */
    private void writeLine(BufferedWriter writer, String[] values) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            if (value == null) {
                value = "";
            }
            // CSV Escaping
            boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
                    || value.contains("\r");

            if (needsQuotes) {
                value = value.replace("\"", "\"\"");
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }

            if (i < values.length - 1) {
                sb.append(",");
            }
        }
        sb.append(System.lineSeparator());
        writer.write(sb.toString());
    }

    /**
     * Main entry point for Digidak Export Operation.
     */
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        // Ensure logs directory exists
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        Properties prop = new Properties();
        try (InputStream input = DigidakExportOperation.class.getClassLoader().getResourceAsStream("application.properties")) {

            if (input == null) {
                logger.error("Sorry, unable to find application.properties");
                return;
            }
            prop.load(input);

            String repoName = prop.getProperty("dctm.repository");
            String userName = prop.getProperty("dctm.username");
            String password = prop.getProperty("dctm.password");
            String whereClause = prop.getProperty("query.where.clause");
            String exportPath = prop.getProperty("path.export");

            int threadCount = 10;
            String threadCountStr = prop.getProperty("export.thread.count");
            if (threadCountStr != null && !threadCountStr.isEmpty()) {
                try {
                    threadCount = Integer.parseInt(threadCountStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid export.thread.count property. Using default: " + threadCount);
                }
            }

            int timeoutHours = 24;
            String timeoutStr = prop.getProperty("export.timeout.hours");
            if (timeoutStr != null && !timeoutStr.isEmpty()) {
                try {
                    timeoutHours = Integer.parseInt(timeoutStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid export.timeout.hours property. Using default: " + timeoutHours);
                }
            }

            logger.info(
                    "====================================================================================================");
            logger.info("Starting Digidak Export Operation");
            logger.info("Repository: " + repoName);
            logger.info("User: " + userName);
            logger.info("Where Clause: " + whereClause);
            logger.info("Export Path: " + exportPath);
            logger.info("Thread Count: " + threadCount);
            logger.info("Timeout (Hours): " + timeoutHours);
            logger.info(
                    "====================================================================================================");

            if (exportPath == null || exportPath.trim().isEmpty()) {
                exportPath = "data/DigidakMetadata_Export.csv";
            } else {
                // If path doesn't end in .csv, treat it as a directory
                if (!exportPath.toLowerCase().endsWith(".csv")) {
                    File dir = new File(exportPath);
                    if (!dir.exists()) {
                        boolean created = dir.mkdirs();
                        if (created) {
                            logger.info("Created export directory: " + dir.getAbsolutePath());
                        }
                    }

                    if (!exportPath.endsWith(File.separator)) {
                        exportPath += File.separator;
                    }
                    exportPath += "DigidakMetadata_Export.csv";
                } else {
                    // It is a specific file path. Ensure parent directory exists.
                    File file = new File(exportPath);
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                }
            }

            // Initialize Session Manager
            DocumentumSessionManager.initSessionManager(repoName, userName, password);

            // Get Session
            IDfSession session = DocumentumSessionManager.getSession(repoName);
            logger.info("Session created. Connected to Docbase: " + session.getDocbaseName());

            // Run Export
            DigidakExportOperation exporter = new DigidakExportOperation();
            
            // Export keywords
            exporter.extractDigidakKeywordsCSV(session, exportPath, whereClause);
            
            // Export Single Records (non-bulk, non-group letters)
            // WHERE: DATETOSTRING("r_creation_date", 'yyyy')='2024' AND group_letter_id=true AND bulk_letter !='true'
            exporter.extractSingleRecordsCSV(session, exportPath, repoName, threadCount, timeoutHours);
            
            // Export Group Records
            // WHERE: DATETOSTRING("r_creation_date", 'yyyy')='2024' AND group_letter_id=true
            exporter.extractGroupRecordsCSV(session, exportPath, repoName, threadCount, timeoutHours);
            
            // Export Subletter Records (bulk letters)
            // WHERE: DATETOSTRING("r_creation_date", 'yyyy')='2024' AND group_letter_id=false AND bulk_letter='true'
            exporter.extractSubletterRecordsCSV(session, exportPath, repoName, threadCount, timeoutHours);

            // Release Session
            DocumentumSessionManager.releaseSession(session);

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            long minutes = (totalTime / 1000) / 60;
            long seconds = (totalTime / 1000) % 60;

            logger.info(
                    "====================================================================================================");
            logger.info("Digidak Export Completed Successfully");
            logger.info("Total Execution Time: " + minutes + " min " + seconds + " sec (" + totalTime + " ms)");
            logger.info(
                    "====================================================================================================");

        } catch (Exception e) {
            logger.error("Digidak Export operation failed", e);
            e.printStackTrace();
        }
    }
}
