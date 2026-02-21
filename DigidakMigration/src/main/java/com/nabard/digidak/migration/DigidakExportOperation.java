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
            int threadCount, int timeoutHours, String baseDateFilter) {

        String whereClause = baseDateFilter + " AND group_letter_id=false AND bulk_letter !='true'";
        String recordsDir = "digidak_single_records";
        String csvFileName = "DigidakSingleRecords_Export.csv";

        logger.info("========== Exporting SINGLE Records ==========");
        extractRecordsInternal(session, outputFilePath, whereClause, repoName, threadCount, timeoutHours, recordsDir, csvFileName, true, true);
    }

    /**
     * Extracts Group Digidak records to CSV file.
     * WHERE clause: baseDateFilter AND group_letter_id=true
     */
    public void extractGroupRecordsCSV(IDfSession session, String outputFilePath, String repoName,
            int threadCount, int timeoutHours, String baseDateFilter) {

        String whereClause = baseDateFilter + " AND group_letter_id=true";
        String recordsDir = "digidak_group_records";
        String csvFileName = "DigidakGroupRecords_Export.csv";

        logger.info("========== Exporting GROUP Records ==========");
        extractRecordsInternal(session, outputFilePath, whereClause, repoName, threadCount, timeoutHours, recordsDir, csvFileName, true, false);
    }

    /**
     * Extracts Subletter Digidak records (bulk letters) to CSV file.
     * WHERE clause: baseDateFilter AND group_letter_id=false AND bulk_letter='true'
     * Note: Subletter records only export movement_register.csv (no document metadata)
     */
    public void extractSubletterRecordsCSV(IDfSession session, String outputFilePath, String repoName,
            int threadCount, int timeoutHours, String baseDateFilter) {

        String whereClause = baseDateFilter + " AND group_letter_id=false AND bulk_letter='true'";
        String recordsDir = "digidak_subletter_records";
        String csvFileName = "DigidakSubletterRecords_Export.csv";

        logger.info("========== Exporting SUBLETTER Records (Movement Register Only) ==========");
        extractRecordsInternal(session, outputFilePath, whereClause, repoName, threadCount, timeoutHours, recordsDir, csvFileName, false, true);
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
            int threadCount, int timeoutHours, String recordsDir, String csvFileName, boolean exportDocuments, boolean exportMovementRegister) {
        
        // DQL Query for Digidak (Letter) records from source schema
        // Source type: edmapp_letter_folder
        String baseDql = "SELECT distinct r_object_id, object_name, subject, r_creator_name, r_creation_date, " +
                "status, priority, uid_number, mode_of_receipt, state_of_recipient, sent_to, " +
                "office_region, group_letter_id, responded_object_id, language_type, address_of_recipient, " +
                "sensitivity, region, ref_number, letter_no, financial_year, received_from, " +
                "sub_type, category_external, subjects, category_type, bulk_letter, file_no, type_mode, ho_ro_te, " +
                "from_dept_ro_te, letter_case_number, date_of_receipt, foward_group_id, inward_ref_number, " +
                "assigned_cgm_group, due_date_action, endorse_group_id, is_ddm, vertical_head_group FROM edmapp_letter_folder";

        String dql = baseDql;
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            dql += " WHERE " + whereClause + " AND status !='Saved'";
        } else {
            dql += " WHERE status !='Saved'";
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
            // Columns to export from edmapp_letter_folder
            String[] headers = {
                    "r_object_id", "object_name", "subject", "r_creator_name", "r_creation_date",
                    "status", "priority", "uid_number", "mode_of_receipt", "state_of_recipient",
                    "sent_to", "office_region", "group_letter_id", "responded_object_id",
                    "language_type", "address_of_recipient", "sensitivity", "region", "ref_number",
                    "letter_no", "financial_year", "received_from", "sub_type",
                    "category_external", "subjects", "category_type", "bulk_letter", "file_no", "type_mode",
                    "ho_ro_te", "from_dept_ro_te", "letter_case_number", "date_of_receipt", "foward_group_id",
                    "inward_ref_number", "assigned_cgm_group", "due_date_action", "endorse_group_id", "vertical_head_group"
            };

            // Write Headers
            List<String> outputHeaders = new ArrayList<>(Arrays.asList(headers));
            outputHeaders.add("is_endorsed");
            outputHeaders.add("is_forward");
            outputHeaders.add("is_ddm");
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
                String tempEndorseGroupId = "";
                String tempForwardGroupId = "";
                String tempIsDdm = "";

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
                    } else if (header.equals("endorse_group_id")) {
                        tempEndorseGroupId = val;
                    } else if (header.equals("foward_group_id")) {
                        tempForwardGroupId = val;
                    }
                }

                // Get is_ddm value separately (not in headers array but needed for computation)
                if (collection.hasAttr("is_ddm")) {
                    tempIsDdm = collection.getString("is_ddm");
                }

                // Compute boolean columns
                // is_endorsed: false if endorse_group_id is empty
                boolean isEndorsed = tempEndorseGroupId != null && !tempEndorseGroupId.trim().isEmpty();
                row.add(String.valueOf(isEndorsed));

                // is_forward: false if foward_group_id is empty
                boolean isForward = tempForwardGroupId != null && !tempForwardGroupId.trim().isEmpty();
                row.add(String.valueOf(isForward));

                // is_ddm: true if is_ddm == 'DDM'
                boolean isDdm = "DDM".equalsIgnoreCase(tempIsDdm != null ? tempIsDdm.trim() : "");
                row.add(String.valueOf(isDdm));

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
                                if (exportMovementRegister) {
                                    exportMovementRegister(localSession, digidakDir, digidakObjectId, digidakObjectName, digidakUidNumber);
                                }
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
                "digidak_records", "DigidakMetadata_Export.csv", true, true);
    }

    /**
     * Extracts Digidak keywords to separate CSV file.
     */
    public void extractRepeatingAttributesCSV(IDfSession session, String outputFilePath, String whereClause) {
        // Export each repeating attribute for edmapp_letter_folder to its own CSV file
        String[] folderAttributes = {
            "office_type", "response_to_ioms_id", "endorse_object_id",
            "cgm_and_assigned_groups", "vertical_users", "ddm_vertical_users"
        };

        for (String attr : folderAttributes) {
            exportRepeatingAttribute(session, outputFilePath, whereClause, "edmapp_letter_folder", attr);
        }

        // Export repeating attribute for edmapp_letter_movement_reg
        // We need to link back to the folder to return only relevant records based on the date filter
        // The link is: edmapp_letter_movement_reg.letter_number = edmapp_letter_folder.uid_number
        String movementWhereClause = "letter_number IN (SELECT uid_number FROM edmapp_letter_folder WHERE " + whereClause + ")";
        exportRepeatingAttribute(session, outputFilePath, movementWhereClause, "edmapp_letter_movement_reg", "send_to");

        // Export workflow users based on cgm_and_assigned_groups
        exportWorkflowUsers(session, outputFilePath, whereClause);
    }

    /**
     * Exports a single repeating attribute to a CSV file.
     * IMPROVED APPROACH: Fetch objects and iterate through repeating values programmatically
     */
    private void exportRepeatingAttribute(IDfSession session, String outputBasePath, String whereClause, String typeName, String attributeName) {
        // Query to get object IDs only (not the repeating attribute in the query)
        String dql = "SELECT r_object_id FROM " + typeName;
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            dql += " WHERE " + whereClause;
        }

        logger.info("Exporting repeating attribute: " + attributeName);
        logger.debug("Executing Query: " + dql);

        File mainExportDir = new File(outputBasePath);
        if (!mainExportDir.exists()) {
            mainExportDir.mkdirs();
        }
        File outputFile = new File(mainExportDir, "repeating_" + attributeName + ".csv");

        IDfCollection collection = null;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            IDfQuery query = new DfQuery();
            query.setDQL(dql);
            collection = query.execute(session, IDfQuery.DF_READ_QUERY);

            // Header: r_object_id, [attribute_name]
            String[] headers = { "r_object_id", attributeName };
            writeLine(writer, headers);

            int totalRows = 0;
            int objectCount = 0;

            while (collection.next()) {
                String objectId = collection.getString("r_object_id");
                objectCount++;

                try {
                    // Fetch the object to access repeating attribute values
                    IDfSysObject sysObj = (IDfSysObject) session.getObject(new DfId(objectId));

                    // Get the count of values for this repeating attribute
                    int valueCount = sysObj.getValueCount(attributeName);

                    // If no values, skip this object (don't write empty rows)
                    if (valueCount == 0) {
                        logger.debug("Object " + objectId + " has no values for " + attributeName);
                        continue;
                    }

                    // Iterate through each value in the repeating attribute
                    for (int i = 0; i < valueCount; i++) {
                        String value = sysObj.getRepeatingString(attributeName, i);

                        // Skip null or empty values
                        if (value != null && !value.trim().isEmpty()) {
                            List<String> row = new ArrayList<>();
                            row.add(objectId);  // Correct r_object_id
                            row.add(value);     // Individual repeating value
                            writeLine(writer, row.toArray(new String[0]));
                            totalRows++;
                        }
                    }

                } catch (DfException e) {
                    logger.error("Error fetching object " + objectId + " for attribute " + attributeName + ": " + e.getMessage());
                    // Continue with next object instead of failing entirely
                }
            }

            logger.info("Exported " + totalRows + " rows from " + objectCount + " objects for attribute " + attributeName + " to " + outputFile.getName());

        } catch (DfException | IOException e) {
            logger.error("Error in exportRepeatingAttribute for " + attributeName, e);
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
     * Exports workflow users based on cgm_and_assigned_groups attribute.
     * Fetches all users from dm_group based on group_name values in cgm_and_assigned_groups.
     * IMPROVED APPROACH: Fetch objects and iterate through repeating values programmatically
     */
    private void exportWorkflowUsers(IDfSession session, String outputBasePath, String whereClause) {
        logger.info("Exporting workflow users based on cgm_and_assigned_groups...");

        File mainExportDir = new File(outputBasePath);
        if (!mainExportDir.exists()) {
            mainExportDir.mkdirs();
        }
        File outputFile = new File(mainExportDir, "repeating_workflow_users.csv");

        // Get all records (object IDs only)
        String dql = "SELECT r_object_id FROM edmapp_letter_folder";
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            dql += " WHERE " + whereClause;
        }

        logger.debug("Executing Query: " + dql);

        IDfCollection collection = null;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            IDfQuery query = new DfQuery();
            query.setDQL(dql);
            collection = query.execute(session, IDfQuery.DF_READ_QUERY);

            // Header: r_object_id, workflow_users
            String[] headers = { "r_object_id", "workflow_users" };
            writeLine(writer, headers);

            int totalRows = 0;
            int objectCount = 0;

            while (collection.next()) {
                String objectId = collection.getString("r_object_id");
                objectCount++;

                try {
                    // Fetch the object to access cgm_and_assigned_groups repeating attribute
                    IDfSysObject folderObj = (IDfSysObject) session.getObject(new DfId(objectId));

                    // Get the count of groups assigned
                    int groupCount = folderObj.getValueCount("cgm_and_assigned_groups");

                    if (groupCount == 0) {
                        logger.debug("Object " + objectId + " has no cgm_and_assigned_groups");
                        continue;
                    }

                    // Iterate through each group name
                    for (int i = 0; i < groupCount; i++) {
                        String groupName = folderObj.getRepeatingString("cgm_and_assigned_groups", i);

                        // Skip if no group name
                        if (groupName == null || groupName.trim().isEmpty()) {
                            continue;
                        }

                        // Query dm_group to get users_names for this group (object ID only)
                        String groupDql = "SELECT r_object_id FROM dm_group WHERE group_name = '" + groupName.replace("'", "''") + "'";

                        IDfCollection groupCollection = null;
                        try {
                            IDfQuery groupQuery = new DfQuery();
                            groupQuery.setDQL(groupDql);
                            groupCollection = groupQuery.execute(session, IDfQuery.DF_READ_QUERY);

                            if (groupCollection.next()) {
                                String groupObjectId = groupCollection.getString("r_object_id");

                                // Fetch the group object to access users_names repeating attribute
                                IDfSysObject groupObj = (IDfSysObject) session.getObject(new DfId(groupObjectId));
                                int userCount = groupObj.getValueCount("users_names");

                                // Iterate through each user in the group
                                for (int j = 0; j < userCount; j++) {
                                    String userName = groupObj.getRepeatingString("users_names", j);

                                    if (userName != null && !userName.trim().isEmpty()) {
                                        List<String> row = new ArrayList<>();
                                        row.add(objectId);  // Correct folder r_object_id
                                        row.add(userName);  // Individual user name
                                        writeLine(writer, row.toArray(new String[0]));
                                        totalRows++;
                                    }
                                }
                            }
                        } catch (DfException e) {
                            logger.error("Error querying dm_group for group: " + groupName + ": " + e.getMessage());
                        } finally {
                            if (groupCollection != null) {
                                try {
                                    groupCollection.close();
                                } catch (DfException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                } catch (DfException e) {
                    logger.error("Error processing object " + objectId + " for workflow users: " + e.getMessage());
                }
            }

            logger.info("Exported " + totalRows + " workflow users from " + objectCount + " objects to " + outputFile.getName());

        } catch (DfException | IOException e) {
            logger.error("Error in exportWorkflowUsers", e);
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
                "completion_date, letter_number, r_creation_date " +
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
                    "completion_date", "letter_number"
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
        String docDql = "select distinct r_object_id, object_name, r_object_type, i_folder_id, r_folder_path, r_content_size, a_content_type, r_creator_name, r_creation_date, document_type, title " +
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
                    "r_creation_date", "document_type", "title"
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

            logger.info("Using WHERE clause for ALL exports: " + whereClause);

            // Export Repeating Attributes (uses SAME whereClause)
            exporter.extractRepeatingAttributesCSV(session, exportPath, whereClause);

            // Export Single Records (uses SAME whereClause + additional filters)
            exporter.extractSingleRecordsCSV(session, exportPath, repoName, threadCount, timeoutHours, whereClause);

            // Export Group Records (uses SAME whereClause + additional filters)
            exporter.extractGroupRecordsCSV(session, exportPath, repoName, threadCount, timeoutHours, whereClause);

            // Export Subletter Records (uses SAME whereClause + additional filters)
            exporter.extractSubletterRecordsCSV(session, exportPath, repoName, threadCount, timeoutHours, whereClause);

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
