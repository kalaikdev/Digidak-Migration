package com.digidak.migration.service;

import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.model.FolderInfo;
import com.digidak.migration.repository.RealFolderRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Service for managing folder operations
 */
public class FolderService {
    private static final Logger logger = LogManager.getLogger(FolderService.class);

    private RealFolderRepository folderRepository;
    private MigrationConfig config;
    private Map<String, String> folderIdMap; // Path -> ID mapping

    public FolderService(RealFolderRepository folderRepository, MigrationConfig config) {
        this.folderRepository = folderRepository;
        this.config = config;
        this.folderIdMap = new HashMap<>();
    }

    /**
     * Setup complete folder structure for DigiDak migration
     */
    public void setupFolderStructure() throws Exception {
        logger.info("Setting up folder structure for DigiDak migration");

        // Step 1: Create cabinet
        String cabinetName = config.getCabinetName();
        FolderInfo cabinet = createOrGetCabinet(cabinetName);
        folderIdMap.put(cabinet.getFolderPath(), cabinet.getFolderId());

        // Step 2: Create single records folders
        createSingleRecordsFolders(cabinet);

        // Step 3: Create group records folders
        createGroupRecordsFolders(cabinet);

        // Step 4: Create subletter folders under their respective group folders
        createSubletterFolders();

        // Step 5: Set metadata for all folders from CSV files
        setFolderMetadataFromCSV();

        logger.info("Folder structure setup completed. Total folders created: {}",
                folderIdMap.size());
    }

    /**
     * Create or get cabinet
     */
    private FolderInfo createOrGetCabinet(String cabinetName) throws Exception {
        String cabinetPath = "/" + cabinetName;

        if (folderRepository.folderExists(cabinetPath)) {
            logger.info("Cabinet already exists: {}", cabinetPath);
            return folderRepository.getFolderByPath(cabinetPath);
        }

        return folderRepository.createCabinet(cabinetName);
    }

    /**
     * Create single records folders
     */
    private void createSingleRecordsFolders(FolderInfo cabinet) throws Exception {
        logger.info("Creating single records folders");

        String singleRecordsPath = config.getDataExportPath() + "/digidak_single_records";
        File singleRecordsDir = new File(singleRecordsPath);

        if (!singleRecordsDir.exists() || !singleRecordsDir.isDirectory()) {
            logger.warn("Single records directory not found: {}", singleRecordsPath);
            return;
        }

        File[] folders = singleRecordsDir.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                String folderName = folder.getName();
                FolderInfo createdFolder = folderRepository.createFolder(
                        folderName,
                        cabinet.getFolderPath(),
                        FolderInfo.FolderType.SINGLE_RECORD
                );
                folderIdMap.put(createdFolder.getFolderPath(), createdFolder.getFolderId());
                logger.info("Created single record folder: {}", folderName);
            }
        }
    }

    /**
     * Create group records folders
     */
    private void createGroupRecordsFolders(FolderInfo cabinet) throws Exception {
        logger.info("Creating group records folders");

        String groupRecordsPath = config.getDataExportPath() + "/digidak_group_records";
        File groupRecordsDir = new File(groupRecordsPath);

        if (!groupRecordsDir.exists() || !groupRecordsDir.isDirectory()) {
            logger.warn("Group records directory not found: {}", groupRecordsPath);
            return;
        }

        File[] folders = groupRecordsDir.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                String folderName = folder.getName();
                FolderInfo createdFolder = folderRepository.createFolder(
                        folderName,
                        cabinet.getFolderPath(),
                        FolderInfo.FolderType.GROUP_RECORD
                );
                folderIdMap.put(createdFolder.getFolderPath(), createdFolder.getFolderId());
                logger.info("Created group record folder: {}", folderName);
            }
        }
    }

    /**
     * Create subletter folders under their respective group folders
     * Note: Subletters are created inside their parent group folders
     * Example: 4245-2024-25 will be created inside G65-2024-25
     */
    private void createSubletterFolders() throws Exception {
        logger.info("Creating subletter folders under their respective group folders");

        String subletterRecordsPath = config.getDataExportPath() + "/digidak_subletter_records";
        File subletterRecordsDir = new File(subletterRecordsPath);

        if (!subletterRecordsDir.exists() || !subletterRecordsDir.isDirectory()) {
            logger.warn("Subletter records directory not found: {}", subletterRecordsPath);
            return;
        }

        File[] folders = subletterRecordsDir.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                String subletterName = folder.getName();

                // Determine parent group folder from metadata or configuration
                // This reads the subletter's parent group from metadata CSV or config
                String parentFolderName = getParentGroupForSubletter(subletterName, folder);

                String cabinetName = config.getCabinetName();
                String parentPath = "/" + cabinetName + "/" + parentFolderName;

                // Only create if parent group folder exists
                if (folderRepository.folderExists(parentPath)) {
                    FolderInfo createdFolder = folderRepository.createFolder(
                            subletterName,
                            parentPath,
                            FolderInfo.FolderType.SUBLETTER_RECORD
                    );
                    folderIdMap.put(createdFolder.getFolderPath(), createdFolder.getFolderId());
                    logger.info("Created subletter folder: {} inside group folder: {}",
                               subletterName, parentFolderName);
                } else {
                    logger.warn("Parent group folder '{}' not found for subletter: {}",
                               parentFolderName, subletterName);
                }
            }
        }
    }

    /**
     * Get parent group folder for a subletter
     * Priority:
     * 1. Check metadata CSV for parent folder reference
     * 2. Check configuration file for mapping
     * 3. Use first available group folder (default)
     *
     * In production, this would read from:
     * - Document metadata CSV (i_folder_id or r_folder_path)
     * - Configuration file with explicit mapping
     * - Database lookup
     */
    private String getParentGroupForSubletter(String subletterName, File subletterFolder) {
        // Option 1: Read from metadata CSV
        String parentFromMetadata = readParentFromMetadata(subletterFolder);
        if (parentFromMetadata != null) {
            logger.debug("Found parent group from metadata for {}: {}",
                        subletterName, parentFromMetadata);
            return parentFromMetadata;
        }

        // Option 2: Check configuration for explicit mapping
        String parentFromConfig = config.getProperty("subletter." + subletterName + ".parent");
        if (parentFromConfig != null) {
            logger.debug("Found parent group from config for {}: {}",
                        subletterName, parentFromConfig);
            return parentFromConfig;
        }

        // Option 3: Default - use first available group folder
        // For the sample data: All subletters go under G65-2024-25
        String parentFromDirectory = getFirstGroupFolder();
        logger.info("Using default parent group for subletter {}: {}",
                   subletterName, parentFromDirectory);
        return parentFromDirectory;
    }

    /**
     * Read parent group folder from subletter's metadata CSV
     * Reads from the main DigidakSubletterRecords_Export.csv file
     */
    private String readParentFromMetadata(File subletterFolder) {
        String subletterName = subletterFolder.getName();
        File exportCsv = new File(config.getDataExportPath() + "/DigidakSubletterRecords_Export.csv");

        if (!exportCsv.exists()) {
            logger.warn("DigidakSubletterRecords_Export.csv not found");
            return null;
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(exportCsv))) {
            // Use a simple CSV parser from opencsv
            com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);

            // Read header
            String[] headers = csvReader.readNext();
            if (headers == null) {
                csvReader.close();
                return null;
            }

            // Find column indices
            int objectNameIndex = -1;
            int groupIdIndex = -1;
            for (int i = 0; i < headers.length; i++) {
                if ("object_name".equals(headers[i].trim())) {
                    objectNameIndex = i;
                } else if ("group_id".equals(headers[i].trim())) {
                    groupIdIndex = i;
                }
            }

            if (objectNameIndex == -1 || groupIdIndex == -1) {
                logger.warn("Required columns not found in DigidakSubletterRecords_Export.csv");
                csvReader.close();
                return null;
            }

            // Read data rows
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                if (values.length > Math.max(objectNameIndex, groupIdIndex)) {
                    String rowName = values[objectNameIndex].trim();
                    if (subletterName.equals(rowName)) {
                        String groupId = values[groupIdIndex].trim();
                        // Normalize group_id format: Convert 'G67/2024-25' to 'G67-2024-25'
                        groupId = groupId.replace("/", "-");
                        logger.debug("Found group_id for {}: {} (normalized)", subletterName, groupId);
                        csvReader.close();
                        return groupId;
                    }
                }
            }
            csvReader.close();

        } catch (Exception e) {
            logger.warn("Error reading parent from DigidakSubletterRecords_Export.csv for {}: {}",
                       subletterName, e.getMessage());
        }

        return null;
    }

    /**
     * Get first available group folder (fallback)
     */
    private String getFirstGroupFolder() {
        String groupRecordsPath = config.getDataExportPath() + "/digidak_group_records";
        File groupRecordsDir = new File(groupRecordsPath);

        if (groupRecordsDir.exists() && groupRecordsDir.isDirectory()) {
            File[] folders = groupRecordsDir.listFiles(File::isDirectory);
            if (folders != null && folders.length > 0) {
                // Return first group folder (e.g., G65-2024-25)
                return folders[0].getName();
            }
        }

        logger.warn("No group folders found, using default");
        return "default_group";
    }

    /**
     * Get folder ID by path
     */
    public String getFolderIdByPath(String folderPath) {
        return folderIdMap.get(folderPath);
    }

    /**
     * Get folder ID by name
     */
    public String getFolderIdByName(String folderName) throws Exception {
        String cabinetName = config.getCabinetName();
        String folderPath = "/" + cabinetName + "/" + folderName;
        return folderIdMap.get(folderPath);
    }

    /**
     * Get all created folder IDs
     */
    public Map<String, String> getAllFolderIds() {
        return new HashMap<>(folderIdMap);
    }

    /**
     * Load existing folder structure from repository
     * Used when running Phase 2 separately after Phase 1
     */
    public void loadExistingFolderStructure() throws Exception {
        logger.info("Loading existing folder structure from repository");

        String cabinetName = config.getCabinetName();
        String cabinetPath = "/" + cabinetName;

        // Load cabinet
        if (folderRepository.folderExists(cabinetPath)) {
            FolderInfo cabinet = folderRepository.getFolderByPath(cabinetPath);
            folderIdMap.put(cabinet.getFolderPath(), cabinet.getFolderId());
            logger.info("Loaded cabinet: {} [{}]", cabinet.getFolderPath(), cabinet.getFolderId());
        }

        // Load single record folders
        loadFoldersFromDirectory("digidak_single_records", cabinetPath);

        // Load group record folders and their subletters
        loadFoldersFromDirectory("digidak_group_records", cabinetPath);

        // Load subletter folders (check under each group folder)
        loadSubletterFolders(cabinetPath);

        logger.info("Loaded {} folders into cache", folderIdMap.size());
    }

    private void loadFoldersFromDirectory(String dirName, String cabinetPath) throws Exception {
        String dataPath = config.getDataExportPath() + "/" + dirName;
        File dir = new File(dataPath);

        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("Directory not found: {}", dataPath);
            return;
        }

        File[] folders = dir.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                String folderName = folder.getName();
                String folderPath = cabinetPath + "/" + folderName;

                if (folderRepository.folderExists(folderPath)) {
                    FolderInfo folderInfo = folderRepository.getFolderByPath(folderPath);
                    folderIdMap.put(folderInfo.getFolderPath(), folderInfo.getFolderId());
                    logger.debug("Loaded folder: {} [{}]", folderInfo.getFolderPath(), folderInfo.getFolderId());
                }
            }
        }
    }

    private void loadSubletterFolders(String cabinetPath) throws Exception {
        String dataPath = config.getDataExportPath() + "/digidak_subletter_records";
        File dir = new File(dataPath);

        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("Subletter directory not found: {}", dataPath);
            return;
        }

        File[] folders = dir.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                String subletterName = folder.getName();

                // Find parent group folder
                String parentGroupName = getParentGroupForSubletter(subletterName, folder);
                String subletterPath = cabinetPath + "/" + parentGroupName + "/" + subletterName;

                if (folderRepository.folderExists(subletterPath)) {
                    FolderInfo folderInfo = folderRepository.getFolderByPath(subletterPath);
                    folderIdMap.put(folderInfo.getFolderPath(), folderInfo.getFolderId());
                    logger.debug("Loaded subletter folder: {} [{}]", folderInfo.getFolderPath(), folderInfo.getFolderId());
                }
            }
        }
    }

    /**
     * Set metadata for all folders from CSV files
     */
    private void setFolderMetadataFromCSV() throws Exception {
        logger.info("Setting folder metadata from CSV files");

        // Set metadata for single record folders
        setMetadataForFolderType("digidak_single_records", "DigidakSingleRecords_Export.csv");

        // Set metadata for group record folders
        setMetadataForFolderType("digidak_group_records", "DigidakGroupRecords_Export.csv");

        // Set metadata for subletter folders
        setMetadataForFolderType("digidak_subletter_records", "DigidakSubletterRecords_Export.csv");

        logger.info("Folder metadata setting completed");
    }

    /**
     * Set metadata for a specific folder type
     */
    private void setMetadataForFolderType(String folderTypeDir, String csvFileName) throws Exception {
        File csvFile = new File(config.getDataExportPath() + "/" + csvFileName);
        if (!csvFile.exists()) {
            logger.warn("CSV file not found: {}", csvFile.getAbsolutePath());
            return;
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(csvFile))) {
            com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);

            // Read header
            String[] headers = csvReader.readNext();
            if (headers == null) {
                csvReader.close();
                return;
            }

            // Find column indices for required metadata
            int objectNameIndex = findColumnIndex(headers, "object_name");
            int subjectsIndex = findColumnIndex(headers, "subjects");
            int priorityIndex = findColumnIndex(headers, "priority");
            int uidNumberIndex = findColumnIndex(headers, "uid_number");
            int rCreatorNameIndex = findColumnIndex(headers, "r_creator_name");
            int rCreationDateIndex = findColumnIndex(headers, "r_creation_date");
            int modeOfReceiptIndex = findColumnIndex(headers, "mode_of_receipt");
            int stateOfRecipientIndex = findColumnIndex(headers, "state_of_recipient");
            int sentToIndex = findColumnIndex(headers, "sent_to");
            int officeRegionIndex = findColumnIndex(headers, "office_region");
            int groupLetterIdIndex = findColumnIndex(headers, "group_letter_id");
            int languageTypeIndex = findColumnIndex(headers, "language_type");
            int addressOfRecipientIndex = findColumnIndex(headers, "address_of_recipient");
            int sensitivityIndex = findColumnIndex(headers, "sensitivity");
            int regionIndex = findColumnIndex(headers, "region");
            int letterNoIndex = findColumnIndex(headers, "letter_no");
            int financialYearIndex = findColumnIndex(headers, "financial_year");
            int receivedFromIndex = findColumnIndex(headers, "received_from");
            int subTypeIndex = findColumnIndex(headers, "sub_type");
            int categoryExternalIndex = findColumnIndex(headers, "category_external");
            int categoryTypeIndex = findColumnIndex(headers, "category_type");
            int fileNoIndex = findColumnIndex(headers, "file_no");
            int bulkLetterIndex = findColumnIndex(headers, "bulk_letter");
            int typeModeIndex = findColumnIndex(headers, "type_mode");
            int hoRoTeIndex = findColumnIndex(headers, "ho_ro_te");
            int fromDeptRoTeIndex = findColumnIndex(headers, "from_dept_ro_te");
            int rObjectIdIndex = findColumnIndex(headers, "r_object_id");

            // Read data rows
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                if (values.length > objectNameIndex && objectNameIndex >= 0) {
                    String folderName = values[objectNameIndex].trim();

                    // Find folder ID
                    String folderId = findFolderIdForType(folderName, folderTypeDir);

                    if (folderId != null && !folderId.isEmpty()) {
                        // Prepare metadata attributes
                        Map<String, Object> attributes = new HashMap<>();

                        // Map CSV columns to folder attributes per requirements
                        if (subjectsIndex >= 0 && subjectsIndex < values.length && !values[subjectsIndex].trim().isEmpty()) {
                            attributes.put("letter_subject", values[subjectsIndex].trim());
                        }
                        if (priorityIndex >= 0 && priorityIndex < values.length && !values[priorityIndex].trim().isEmpty()) {
                            attributes.put("priority", values[priorityIndex].trim());
                        }
                        if (uidNumberIndex >= 0 && uidNumberIndex < values.length && !values[uidNumberIndex].trim().isEmpty()) {
                            attributes.put("uid_number", values[uidNumberIndex].trim());
                        }
                        if (rCreatorNameIndex >= 0 && rCreatorNameIndex < values.length && !values[rCreatorNameIndex].trim().isEmpty()) {
                            attributes.put("initiator", values[rCreatorNameIndex].trim());
                        }
                        if (rCreationDateIndex >= 0 && rCreationDateIndex < values.length && !values[rCreationDateIndex].trim().isEmpty()) {
                            attributes.put("r_creation_date", values[rCreationDateIndex].trim());
                        }
                        if (modeOfReceiptIndex >= 0 && modeOfReceiptIndex < values.length && !values[modeOfReceiptIndex].trim().isEmpty()) {
                            attributes.put("mode_of_receipt", values[modeOfReceiptIndex].trim());
                        }
                        if (stateOfRecipientIndex >= 0 && stateOfRecipientIndex < values.length && !values[stateOfRecipientIndex].trim().isEmpty()) {
                            attributes.put("state_of_sender", values[stateOfRecipientIndex].trim());
                        }
                        if (sentToIndex >= 0 && sentToIndex < values.length && !values[sentToIndex].trim().isEmpty()) {
                            attributes.put("decision", values[sentToIndex].trim());
                        }
                        if (officeRegionIndex >= 0 && officeRegionIndex < values.length && !values[officeRegionIndex].trim().isEmpty()) {
                            attributes.put("selected_region", values[officeRegionIndex].trim());
                        }
                        if (groupLetterIdIndex >= 0 && groupLetterIdIndex < values.length && !values[groupLetterIdIndex].trim().isEmpty()) {
                            attributes.put("is_group", values[groupLetterIdIndex].trim());
                        }
                        if (languageTypeIndex >= 0 && languageTypeIndex < values.length && !values[languageTypeIndex].trim().isEmpty()) {
                            attributes.put("languages", values[languageTypeIndex].trim());
                        }
                        if (addressOfRecipientIndex >= 0 && addressOfRecipientIndex < values.length && !values[addressOfRecipientIndex].trim().isEmpty()) {
                            attributes.put("address_of_sender", values[addressOfRecipientIndex].trim());
                        }
                        if (sensitivityIndex >= 0 && sensitivityIndex < values.length && !values[sensitivityIndex].trim().isEmpty()) {
                            attributes.put("secrecy", values[sensitivityIndex].trim());
                        }
                        if (regionIndex >= 0 && regionIndex < values.length && !values[regionIndex].trim().isEmpty()) {
                            attributes.put("region", values[regionIndex].trim());
                        }
                        if (letterNoIndex >= 0 && letterNoIndex < values.length && !values[letterNoIndex].trim().isEmpty()) {
                            attributes.put("letter_no", values[letterNoIndex].trim());
                        }
                        if (financialYearIndex >= 0 && financialYearIndex < values.length && !values[financialYearIndex].trim().isEmpty()) {
                            attributes.put("financial_year", values[financialYearIndex].trim());
                        }
                        if (receivedFromIndex >= 0 && receivedFromIndex < values.length && !values[receivedFromIndex].trim().isEmpty()) {
                            attributes.put("received_from", values[receivedFromIndex].trim());
                        }
                        if (subTypeIndex >= 0 && subTypeIndex < values.length && !values[subTypeIndex].trim().isEmpty()) {
                            attributes.put("nature_of_correspondence", values[subTypeIndex].trim());
                        }
                        // Note: category_external also maps to received_from (duplicate in requirements)
                        if (categoryTypeIndex >= 0 && categoryTypeIndex < values.length && !values[categoryTypeIndex].trim().isEmpty()) {
                            attributes.put("type_category", values[categoryTypeIndex].trim());
                        }
                        if (fileNoIndex >= 0 && fileNoIndex < values.length && !values[fileNoIndex].trim().isEmpty()) {
                            attributes.put("file_number", values[fileNoIndex].trim());
                        }
                        if (bulkLetterIndex >= 0 && bulkLetterIndex < values.length && !values[bulkLetterIndex].trim().isEmpty()) {
                            attributes.put("is_bulk_letter", values[bulkLetterIndex].trim());
                        }
                        if (typeModeIndex >= 0 && typeModeIndex < values.length && !values[typeModeIndex].trim().isEmpty()) {
                            attributes.put("entry_type", values[typeModeIndex].trim());
                        }
                        if (hoRoTeIndex >= 0 && hoRoTeIndex < values.length && !values[hoRoTeIndex].trim().isEmpty()) {
                            attributes.put("login_office_type", values[hoRoTeIndex].trim());
                        }
                        if (fromDeptRoTeIndex >= 0 && fromDeptRoTeIndex < values.length && !values[fromDeptRoTeIndex].trim().isEmpty()) {
                            attributes.put("login_region", values[fromDeptRoTeIndex].trim());
                        }
                        if (rObjectIdIndex >= 0 && rObjectIdIndex < values.length && !values[rObjectIdIndex].trim().isEmpty()) {
                            attributes.put("migrated_id", values[rObjectIdIndex].trim());
                        }

                        // Always set these values
                        attributes.put("status", "Closed");
                        attributes.put("is_migrated", true);

                        // Set metadata
                        folderRepository.setFolderMetadata(folderId, attributes);
                        logger.debug("Set metadata for folder: {} [{}]", folderName, folderId);
                    }
                }
            }
            csvReader.close();
        } catch (Exception e) {
            logger.error("Error setting metadata from {}: {}", csvFileName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Find folder ID for a given folder name and type
     */
    private String findFolderIdForType(String folderName, String folderTypeDir) {
        String cabinetName = config.getCabinetName();

        // Try direct path first (for single and group records)
        String directPath = "/" + cabinetName + "/" + folderName;
        String folderId = folderIdMap.get(directPath);
        if (folderId != null) {
            return folderId;
        }

        // For subletter folders, search under all group folders
        if ("digidak_subletter_records".equals(folderTypeDir)) {
            for (Map.Entry<String, String> entry : folderIdMap.entrySet()) {
                String path = entry.getKey();
                if (path.endsWith("/" + folderName)) {
                    return entry.getValue();
                }
            }
        }

        return null;
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
}
