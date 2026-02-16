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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

/**
 * Service for managing folder operations
 */
public class FolderService {
    private static final Logger logger = LogManager.getLogger(FolderService.class);

    // Region to short_code mapping for RO/TE ACL names
    private static final Map<String, String> REGION_SHORT_CODE_MAP = new HashMap<>();
    static {
        REGION_SHORT_CODE_MAP.put("andaman and nicobar", "an");
        REGION_SHORT_CODE_MAP.put("andhra pradesh", "ad");
        REGION_SHORT_CODE_MAP.put("arunachal pradesh", "ar");
        REGION_SHORT_CODE_MAP.put("assam", "as");
        REGION_SHORT_CODE_MAP.put("bihar", "br");
        REGION_SHORT_CODE_MAP.put("bird kolkata", "bk");
        REGION_SHORT_CODE_MAP.put("bird lucknow", "bl");
        REGION_SHORT_CODE_MAP.put("bird mangalore", "bm");
        REGION_SHORT_CODE_MAP.put("chhattisgarh", "ch");
        REGION_SHORT_CODE_MAP.put("goa", "ga");
        REGION_SHORT_CODE_MAP.put("gujarat", "gj");
        REGION_SHORT_CODE_MAP.put("haryana", "hr");
        REGION_SHORT_CODE_MAP.put("himachal pradesh", "hp");
        REGION_SHORT_CODE_MAP.put("jammu and kashmir", "jk");
        REGION_SHORT_CODE_MAP.put("jharkhand", "jh");
        REGION_SHORT_CODE_MAP.put("karnataka", "ka");
        REGION_SHORT_CODE_MAP.put("kerala", "kl");
        REGION_SHORT_CODE_MAP.put("madhya pradesh", "mp");
        REGION_SHORT_CODE_MAP.put("maharashtra", "mh");
        REGION_SHORT_CODE_MAP.put("manipur", "mn");
        REGION_SHORT_CODE_MAP.put("meghalaya", "ml");
        REGION_SHORT_CODE_MAP.put("mizoram", "mz");
        REGION_SHORT_CODE_MAP.put("nagaland", "nl");
        REGION_SHORT_CODE_MAP.put("nbsc lucknow", "nc");
        REGION_SHORT_CODE_MAP.put("new delhi", "dl");
        REGION_SHORT_CODE_MAP.put("odisha", "or");
        REGION_SHORT_CODE_MAP.put("punjab", "pn");
        REGION_SHORT_CODE_MAP.put("rajasthan", "rj");
        REGION_SHORT_CODE_MAP.put("sikkim", "sk");
        REGION_SHORT_CODE_MAP.put("tamilnadu", "tn");
        REGION_SHORT_CODE_MAP.put("telangana", "tg");
        REGION_SHORT_CODE_MAP.put("tripura", "tr");
        REGION_SHORT_CODE_MAP.put("uttar pradesh", "up");
        REGION_SHORT_CODE_MAP.put("uttarakhand", "uk");
        REGION_SHORT_CODE_MAP.put("west bengal", "wb");
    }

    private RealFolderRepository folderRepository;
    private MigrationConfig config;
    private Map<String, String> folderIdMap; // Path -> ID mapping
    private UserLookupService userLookupService;
    private AclService aclService;

    public FolderService(RealFolderRepository folderRepository, MigrationConfig config,
                        UserLookupService userLookupService, AclService aclService) {
        this.folderRepository = folderRepository;
        this.config = config;
        this.folderIdMap = new HashMap<>();
        this.userLookupService = userLookupService;
        this.aclService = aclService;
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
            int verticalHeadGroupIndex = findColumnIndex(headers, "vertical_head_group");
            int endorseGroupIdIndex = findColumnIndex(headers, "endorse_group_id");
            int letterCaseNumberIndex = findColumnIndex(headers, "letter_case_number");
            int dateOfReceiptIndex = findColumnIndex(headers, "date_of_receipt");
            int fowardGroupIdIndex = findColumnIndex(headers, "foward_group_id");
            int inwardRefNumberIndex = findColumnIndex(headers, "inward_ref_number");
            int isEndorsedIndex = findColumnIndex(headers, "is_endorsed");
            int isForwardIndex = findColumnIndex(headers, "is_forward");
            int assignedCgmGroupIndex = findColumnIndex(headers, "assigned_cgm_group");
            int dueDateActionIndex = findColumnIndex(headers, "due_date_action");
            int isDdmIndex = findColumnIndex(headers, "is_ddm");
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
                            attributes.put("r_creation_date", convertDateFormat(values[rCreationDateIndex].trim()));
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
                            attributes.put("is_bulk_letter", values[bulkLetterIndex].trim().toLowerCase());
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
                        if (verticalHeadGroupIndex >= 0 && verticalHeadGroupIndex < values.length && !values[verticalHeadGroupIndex].trim().isEmpty()) {
                            attributes.put("vertical_head_display_name", values[verticalHeadGroupIndex].trim());
                        }
                        if (endorseGroupIdIndex >= 0 && endorseGroupIdIndex < values.length && !values[endorseGroupIdIndex].trim().isEmpty()) {
                            attributes.put("endorse_uid", values[endorseGroupIdIndex].trim());
                        }
                        if (letterCaseNumberIndex >= 0 && letterCaseNumberIndex < values.length && !values[letterCaseNumberIndex].trim().isEmpty()) {
                            attributes.put("case_number", values[letterCaseNumberIndex].trim());
                        }
                        if (dateOfReceiptIndex >= 0 && dateOfReceiptIndex < values.length && !values[dateOfReceiptIndex].trim().isEmpty()) {
                            attributes.put("entry_date", convertDateFormat(values[dateOfReceiptIndex].trim()));
                        }
                        if (fowardGroupIdIndex >= 0 && fowardGroupIdIndex < values.length && !values[fowardGroupIdIndex].trim().isEmpty()) {
                            attributes.put("forward_group_uid", values[fowardGroupIdIndex].trim());
                        }
                        if (inwardRefNumberIndex >= 0 && inwardRefNumberIndex < values.length && !values[inwardRefNumberIndex].trim().isEmpty()) {
                            attributes.put("inward_ref_number", values[inwardRefNumberIndex].trim());
                        }
                        if (isEndorsedIndex >= 0 && isEndorsedIndex < values.length && !values[isEndorsedIndex].trim().isEmpty()) {
                            attributes.put("is_endorsed", values[isEndorsedIndex].trim());
                            attributes.put("is_endorsed_letter", values[isEndorsedIndex].trim()); // Same source, different target
                        }
                        if (isForwardIndex >= 0 && isForwardIndex < values.length && !values[isForwardIndex].trim().isEmpty()) {
                            attributes.put("is_forward", values[isForwardIndex].trim());
                        }
                        if (assignedCgmGroupIndex >= 0 && assignedCgmGroupIndex < values.length && !values[assignedCgmGroupIndex].trim().isEmpty()) {
                            attributes.put("selected_cgm_group", values[assignedCgmGroupIndex].trim());
                        }
                        if (dueDateActionIndex >= 0 && dueDateActionIndex < values.length && !values[dueDateActionIndex].trim().isEmpty()) {
                            attributes.put("due_date", values[dueDateActionIndex].trim());
                        }
                        if (isDdmIndex >= 0 && isDdmIndex < values.length && !values[isDdmIndex].trim().isEmpty()) {
                            attributes.put("is_ddm", values[isDdmIndex].trim());
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

                        // Set repeating attributes from separate CSV files
                        String migratedId = (rObjectIdIndex >= 0 && rObjectIdIndex < values.length)
                                ? values[rObjectIdIndex].trim() : null;
                        if (migratedId != null && !migratedId.isEmpty()) {
                            setRepeatingAttributes(folderId, migratedId);
                        }
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

    /**
     * Set repeating attributes for a folder from separate CSV files
     * Per requirements:
     * - office_type -> source_vertical
     * - response_to_ioms_id -> responding_uid
     * - vertical_users -> vertical_users
     * - ddm_vertical_users -> ddm_users
     * - workflow_users -> workflow_groups
     */
    private void setRepeatingAttributes(String folderId, String migratedId) {
        logger.debug("Setting repeating attributes for folder: {} (migrated_id: {})",
                     folderId, migratedId);

        // Set each repeating attribute from its respective CSV file
        setRepeatingAttribute(folderId, migratedId, "repeating_office_type.csv",
                             "office_type", "source_vertical");
        setRepeatingAttribute(folderId, migratedId, "repeating_response_to_ioms_id.csv",
                             "response_to_ioms_id", "responding_uid");
        setRepeatingAttribute(folderId, migratedId, "repeating_vertical_users.csv",
                             "vertical_users", "vertical_users");
        setRepeatingAttribute(folderId, migratedId, "repeating_ddm_vertical_users.csv",
                             "ddm_vertical_users", "ddm_users");
        setRepeatingAttribute(folderId, migratedId, "repeating_workflow_users.csv",
                             "workflow_users", "workflow_groups");

        // Apply ACL permissions for workflow users
        applyWorkflowUserAcls(folderId, migratedId);
    }

    /**
     * Apply ACL permissions for workflow users
     * Called after setting workflow_groups repeating attribute
     * For group folders (object_name starts with "G"), collects workflow users from all subletter children
     * and creates ACL with prefix acl_digidakG_
     */
    private void applyWorkflowUserAcls(String folderId, String migratedId) {
        System.out.println("=== ACL DEBUG === Applying workflow user ACLs for folder: " + folderId + " (migrated_id: " + migratedId + ")");
        logger.info("=== ACL DEBUG === Applying workflow user ACLs for folder: {} (migrated_id: {})",
                     folderId, migratedId);

        try {
            // Read login_office_type and login_region for this folder
            String[] officeTypeAndRegion = readOfficeTypeAndRegion(migratedId);
            String loginOfficeType = (officeTypeAndRegion != null) ? officeTypeAndRegion[0] : "";
            String loginRegion = (officeTypeAndRegion != null) ? officeTypeAndRegion[1] : "";

            System.out.println("=== ACL DEBUG === login_office_type='" + loginOfficeType + "', login_region='" + loginRegion + "' for migrated_id: " + migratedId);

            // Group folders (starts with "G"): collect subletter workflow_groups and apply as permissions
            boolean isGroupFolder = isGroupFolder(migratedId);
            if (isGroupFolder) {
                System.out.println("=== ACL DEBUG === Group folder detected for migrated_id: " + migratedId);
                List<String> subletterMigratedIds = getSubletterMigratedIds();
                Set<String> allWorkflowGroups = new java.util.LinkedHashSet<>();
                String groupAclName = null;

                for (String subMigratedId : subletterMigratedIds) {
                    String[] subOfficeRegion = readOfficeTypeAndRegion(subMigratedId);
                    if (subOfficeRegion == null) continue;
                    String subOfficeType = subOfficeRegion[0];
                    String subRegion = subOfficeRegion[1];

                    if ("HO".equalsIgnoreCase(subOfficeType)) {
                        String wfGroup = "ecm_ho_" + subRegion.toLowerCase();
                        allWorkflowGroups.add(wfGroup);
                        if (groupAclName == null) groupAclName = "ecm_legacy_digidak_ho";
                    } else if ("RO".equalsIgnoreCase(subOfficeType) || "TE".equalsIgnoreCase(subOfficeType)) {
                        String sc = REGION_SHORT_CODE_MAP.get(subRegion.toLowerCase());
                        if (sc != null) {
                            String wfGroup = "ecm_legacy_digidak_" + sc;
                            allWorkflowGroups.add(wfGroup);
                            if (groupAclName == null) groupAclName = wfGroup;
                        }
                    }
                }

                System.out.println("=== ACL DEBUG === Group folder: collected " + allWorkflowGroups.size() + " unique workflow groups from subletters: " + allWorkflowGroups);

                if (!allWorkflowGroups.isEmpty() && groupAclName != null) {
                    List<String> groupsList = new java.util.ArrayList<>(allWorkflowGroups);

                    // Update workflow_groups repeating attribute
                    try {
                        folderRepository.setRepeatingAttribute(folderId, "workflow_groups", groupsList);
                        System.out.println("=== ACL DEBUG === Updated group folder workflow_groups to: " + groupsList);
                    } catch (Exception wfEx) {
                        System.out.println("=== ACL DEBUG === WARNING: Failed to update group folder workflow_groups: " + wfEx.getMessage());
                        wfEx.printStackTrace();
                    }

                    // Apply ACL with all subletter workflow groups as permissions
                    try {
                        String aclId = aclService.applyExistingAcl(folderId, groupAclName, groupsList);
                        if (aclId != null) {
                            System.out.println("=== ACL DEBUG === SUCCESS: Group folder ACL '" + groupAclName + "' applied with ID: " + aclId);
                        } else {
                            System.out.println("=== ACL DEBUG === FAILED: Group folder ACL application returned null");
                        }
                    } catch (Exception aclEx) {
                        System.out.println("=== ACL DEBUG === EXCEPTION applying group folder ACL: " + aclEx.getMessage());
                        aclEx.printStackTrace();
                    }
                } else {
                    System.out.println("=== ACL DEBUG === No workflow groups resolved for group folder, skipping ACL");
                }
                return;
            }

            // HO folders: use pre-existing ACL ecm_legacy_digidak_ho
            if ("HO".equalsIgnoreCase(loginOfficeType)) {
                String workflowGroupName = "ecm_ho_" + loginRegion.toLowerCase();
                String aclName = "ecm_legacy_digidak_ho";

                System.out.println("=== ACL DEBUG === HO folder detected. Using existing ACL '" + aclName + "' with workflow group '" + workflowGroupName + "'");
                logger.info("=== ACL DEBUG === HO folder. ACL='{}', workflow_group='{}'", aclName, workflowGroupName);

                // Update workflow_groups repeating attribute FIRST
                try {
                    List<String> workflowGroups = new java.util.ArrayList<>();
                    workflowGroups.add(workflowGroupName);
                    folderRepository.setRepeatingAttribute(folderId, "workflow_groups", workflowGroups);
                    System.out.println("=== ACL DEBUG === Updated workflow_groups to: " + workflowGroups);
                } catch (Exception wfEx) {
                    System.out.println("=== ACL DEBUG === WARNING: Failed to update workflow_groups: " + wfEx.getMessage());
                    wfEx.printStackTrace();
                }

                // Apply existing ACL and grant workflow group permission
                try {
                    String aclId = aclService.applyExistingAcl(folderId, aclName, workflowGroupName);
                    if (aclId != null) {
                        System.out.println("=== ACL DEBUG === SUCCESS: HO ACL applied with ID: " + aclId + " for folder: " + folderId);
                    } else {
                        System.out.println("=== ACL DEBUG === FAILED: HO ACL application returned null for folder: " + folderId);
                    }
                } catch (Exception aclEx) {
                    System.out.println("=== ACL DEBUG === EXCEPTION applying HO ACL for folder " + folderId + ": " + aclEx.getMessage());
                    aclEx.printStackTrace();
                }
                return;
            }

            // RO/TE folders: use pre-existing ACL ecm_legacy_digidak_<short_code>
            if ("RO".equalsIgnoreCase(loginOfficeType) || "TE".equalsIgnoreCase(loginOfficeType)) {
                String shortCode = REGION_SHORT_CODE_MAP.get(loginRegion.toLowerCase());
                if (shortCode == null) {
                    System.out.println("=== ACL DEBUG === WARNING: No short_code found for region '" + loginRegion + "', skipping RO/TE ACL");
                    logger.warn("No short_code mapping for region: {}", loginRegion);
                } else {
                    String workflowGroupName = "ecm_legacy_digidak_" + shortCode;
                    String aclName = "ecm_legacy_digidak_" + shortCode;

                    System.out.println("=== ACL DEBUG === " + loginOfficeType + " folder detected. Using existing ACL '" + aclName + "' with workflow group '" + workflowGroupName + "'");

                    // Update workflow_groups repeating attribute FIRST
                    try {
                        List<String> workflowGroups = new java.util.ArrayList<>();
                        workflowGroups.add(workflowGroupName);
                        folderRepository.setRepeatingAttribute(folderId, "workflow_groups", workflowGroups);
                        System.out.println("=== ACL DEBUG === Updated workflow_groups to: " + workflowGroups);
                    } catch (Exception wfEx) {
                        System.out.println("=== ACL DEBUG === WARNING: Failed to update workflow_groups: " + wfEx.getMessage());
                        wfEx.printStackTrace();
                    }

                    // Apply existing ACL and grant workflow group permission
                    try {
                        String aclId = aclService.applyExistingAcl(folderId, aclName, workflowGroupName);
                        if (aclId != null) {
                            System.out.println("=== ACL DEBUG === SUCCESS: " + loginOfficeType + " ACL applied with ID: " + aclId + " for folder: " + folderId);
                        } else {
                            System.out.println("=== ACL DEBUG === FAILED: " + loginOfficeType + " ACL application returned null for folder: " + folderId);
                        }
                    } catch (Exception aclEx) {
                        System.out.println("=== ACL DEBUG === EXCEPTION applying " + loginOfficeType + " ACL for folder " + folderId + ": " + aclEx.getMessage());
                        aclEx.printStackTrace();
                    }
                    return;
                }
            }

            // Fallback: existing logic for folders without HO/RO/TE or unknown regions
            List<String> workflowUserNames = readWorkflowUsersFromCsv(migratedId);
            System.out.println("=== ACL DEBUG === Fallback: Read " + workflowUserNames.size() + " workflow user names from CSV: " + workflowUserNames);

            if (workflowUserNames.isEmpty()) {
                System.out.println("=== ACL DEBUG === No workflow users found for migrated_id: " + migratedId + ", will still create custom ACL");
            }

            System.out.println("=== ACL DEBUG === Creating and applying ACL for folder " + folderId + " with " + workflowUserNames.size() + " workflow users");
            String aclId = aclService.createWorkflowUserAcl(folderId, migratedId, workflowUserNames, false);

            if (aclId != null) {
                System.out.println("=== ACL DEBUG === SUCCESS: ACL created and applied with ID: " + aclId + " for folder: " + folderId);
            } else {
                System.out.println("=== ACL DEBUG === FAILED: ACL creation returned null for folder: " + folderId);
            }

        } catch (Exception e) {
            System.out.println("=== ACL DEBUG === EXCEPTION: Failed to apply workflow ACLs for folder " + folderId + ": " + e.getMessage());
            e.printStackTrace();
            logger.error("=== ACL DEBUG === EXCEPTION: Failed to apply workflow ACLs for folder {}: {}",
                        folderId, e.getMessage(), e);
            // Don't rethrow - let folder creation succeed even if ACL fails
        }
    }

    /**
     * Check if a folder is a group folder by looking up object_name in DigidakGroupRecords_Export.csv
     */
    private boolean isGroupFolder(String migratedId) {
        File csvFile = new File(config.getDataExportPath() + "/DigidakGroupRecords_Export.csv");
        if (!csvFile.exists()) {
            return false;
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(csvFile))) {
            com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);
            String[] headers = csvReader.readNext();
            if (headers == null) {
                csvReader.close();
                return false;
            }

            int rObjectIdIndex = findColumnIndex(headers, "r_object_id");
            if (rObjectIdIndex < 0) {
                csvReader.close();
                return false;
            }

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                if (row.length > rObjectIdIndex && migratedId.equals(row[rObjectIdIndex].trim())) {
                    csvReader.close();
                    return true;
                }
            }
            csvReader.close();
        } catch (Exception e) {
            logger.warn("Error checking if folder is group folder: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Collect workflow users from all subletter children of a group folder
     * Reads subletter r_object_ids from DigidakSubletterRecords_Export.csv,
     * then collects workflow users for each subletter from repeating_workflow_users.csv
     */
    private List<String> collectSubletterWorkflowUsers(String groupMigratedId) {
        Set<String> allUsers = new java.util.LinkedHashSet<>();

        // Step 1: Get all subletter r_object_ids from DigidakSubletterRecords_Export.csv
        List<String> subletterMigratedIds = getSubletterMigratedIds();
        System.out.println("=== GROUP ACL DEBUG === Found " + subletterMigratedIds.size() + " subletters for group folder " + groupMigratedId);
        logger.info("Found {} subletters for group folder {}", subletterMigratedIds.size(), groupMigratedId);

        // Step 2: Collect workflow users from each subletter
        for (String subletterMigratedId : subletterMigratedIds) {
            List<String> subletterUsers = readWorkflowUsersFromCsv(subletterMigratedId);
            if (!subletterUsers.isEmpty()) {
                System.out.println("=== GROUP ACL DEBUG === Subletter " + subletterMigratedId + " has " + subletterUsers.size() + " workflow users: " + subletterUsers);
            }
            allUsers.addAll(subletterUsers);
        }

        System.out.println("=== GROUP ACL DEBUG === Total unique workflow users collected from " + subletterMigratedIds.size() + " subletters: " + allUsers.size() + " users: " + allUsers);
        logger.info("Total unique workflow users collected from {} subletters: {}",
                    subletterMigratedIds.size(), allUsers.size());
        return new java.util.ArrayList<>(allUsers);
    }

    /**
     * Get object_name for a given migrated_id (r_object_id) from a CSV file
     */
    private String getObjectNameByMigratedId(String migratedId, String csvFileName) {
        File csvFile = new File(config.getDataExportPath() + "/" + csvFileName);
        if (!csvFile.exists()) {
            return null;
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(csvFile))) {
            com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);
            String[] headers = csvReader.readNext();
            if (headers == null) {
                csvReader.close();
                return null;
            }

            int rObjectIdIndex = findColumnIndex(headers, "r_object_id");
            int objectNameIndex = findColumnIndex(headers, "object_name");
            if (rObjectIdIndex < 0 || objectNameIndex < 0) {
                csvReader.close();
                return null;
            }

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                if (row.length > Math.max(rObjectIdIndex, objectNameIndex)) {
                    if (migratedId.equals(row[rObjectIdIndex].trim())) {
                        String objectName = row[objectNameIndex].trim();
                        csvReader.close();
                        return objectName;
                    }
                }
            }
            csvReader.close();
        } catch (Exception e) {
            logger.warn("Error reading object_name from {}: {}", csvFileName, e.getMessage());
        }
        return null;
    }

    /**
     * Get all subletter r_object_ids from DigidakSubletterRecords_Export.csv
     */
    private List<String> getSubletterMigratedIds() {
        List<String> migratedIds = new java.util.ArrayList<>();
        File csvFile = new File(config.getDataExportPath() + "/DigidakSubletterRecords_Export.csv");
        if (!csvFile.exists()) {
            System.out.println("=== GROUP ACL DEBUG === Subletter CSV not found: " + csvFile.getAbsolutePath());
            return migratedIds;
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(csvFile))) {
            com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);
            String[] headers = csvReader.readNext();
            if (headers == null) {
                csvReader.close();
                return migratedIds;
            }

            int rObjectIdIndex = findColumnIndex(headers, "r_object_id");
            if (rObjectIdIndex < 0) {
                System.out.println("=== GROUP ACL DEBUG === r_object_id column not found in subletter CSV");
                csvReader.close();
                return migratedIds;
            }

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                if (row.length > rObjectIdIndex) {
                    String subletterMigratedId = row[rObjectIdIndex].trim();
                    if (!subletterMigratedId.isEmpty()) {
                        migratedIds.add(subletterMigratedId);
                    }
                }
            }
            csvReader.close();
        } catch (Exception e) {
            System.out.println("=== GROUP ACL DEBUG === Error reading subletter CSV: " + e.getMessage());
            logger.warn("Error reading subletter migrated IDs: {}", e.getMessage());
        }
        return migratedIds;
    }

    /**
     * Read workflow users from repeating_workflow_users.csv for a specific migrated_id
     */
    private List<String> readWorkflowUsersFromCsv(String migratedId) {
        List<String> users = new java.util.ArrayList<>();
        String csvPath = config.getDataExportPath() + "/repeating_workflow_users.csv";
        File csvFile = new File(csvPath);

        if (!csvFile.exists()) {
            logger.warn("repeating_workflow_users.csv not found at: {}", csvPath);
            return users;
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(csvFile))) {
            com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);

            // Read header
            String[] headers = csvReader.readNext();
            if (headers == null) {
                csvReader.close();
                return users;
            }

            int idIndex = findColumnIndex(headers, "r_object_id");
            int userIndex = findColumnIndex(headers, "workflow_users");

            if (idIndex < 0 || userIndex < 0) {
                logger.warn("Required columns not found in repeating_workflow_users.csv");
                csvReader.close();
                return users;
            }

            // Read rows and collect users for this migrated_id
            String[] row;
            Set<String> uniqueUsers = new java.util.HashSet<>(); // Avoid duplicates

            while ((row = csvReader.readNext()) != null) {
                if (row.length > Math.max(idIndex, userIndex)) {
                    String rowId = row[idIndex].trim();
                    if (migratedId.equals(rowId)) {
                        String userName = row[userIndex].trim();
                        if (!userName.isEmpty()) {
                            uniqueUsers.add(userName);
                        }
                    }
                }
            }

            users.addAll(uniqueUsers);
            csvReader.close();

            logger.debug("Read {} unique workflow users for migrated_id: {}",
                        users.size(), migratedId);

        } catch (Exception e) {
            logger.warn("Error reading workflow users from CSV: {}", e.getMessage());
        }

        return users;
    }

    /**
     * Read login_office_type (ho_ro_te) and login_region (from_dept_ro_te) from CSV for a given migratedId.
     * Searches across Single, Group, and Subletter CSV files.
     * Returns a String array: [login_office_type, login_region] or null if not found.
     */
    private String[] readOfficeTypeAndRegion(String migratedId) {
        String[] csvFiles = {
            "DigidakSingleRecords_Export.csv",
            "DigidakGroupRecords_Export.csv",
            "DigidakSubletterRecords_Export.csv"
        };

        for (String csvFileName : csvFiles) {
            File csvFile = new File(config.getDataExportPath() + "/" + csvFileName);
            if (!csvFile.exists()) continue;

            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(csvFile))) {
                com.opencsv.CSVReader csvReader = new com.opencsv.CSVReader(reader);
                String[] headers = csvReader.readNext();
                if (headers == null) { csvReader.close(); continue; }

                int rObjectIdIndex = findColumnIndex(headers, "r_object_id");
                int hoRoTeIndex = findColumnIndex(headers, "ho_ro_te");
                int fromDeptRoTeIndex = findColumnIndex(headers, "from_dept_ro_te");

                if (rObjectIdIndex < 0 || hoRoTeIndex < 0) { csvReader.close(); continue; }

                String[] row;
                while ((row = csvReader.readNext()) != null) {
                    if (row.length > rObjectIdIndex && migratedId.equals(row[rObjectIdIndex].trim())) {
                        String officeType = (hoRoTeIndex < row.length) ? row[hoRoTeIndex].trim() : "";
                        String region = (fromDeptRoTeIndex >= 0 && fromDeptRoTeIndex < row.length) ? row[fromDeptRoTeIndex].trim() : "";
                        csvReader.close();
                        return new String[] { officeType, region };
                    }
                }
                csvReader.close();
            } catch (Exception e) {
                logger.warn("Error reading office type from {}: {}", csvFileName, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Set a single repeating attribute from a CSV file
     */
    private void setRepeatingAttribute(String folderId, String migratedId,
                                       String csvFileName, String sourceColumn,
                                       String targetAttribute) {
        String csvPath = config.getDataExportPath() + "/" + csvFileName;
        File csvFile = new File(csvPath);

        if (!csvFile.exists()) {
            logger.debug("{} not found, skipping repeating attribute: {}",
                        csvFileName, targetAttribute);
            return;
        }

        java.util.List<String> values = new java.util.ArrayList<>();

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
                logger.debug("Using r_object_id column for matching in {} (migrated_id column not found)",
                            csvFileName);
            }
            int valueIndex = findColumnIndex(headers, sourceColumn);

            if (migratedIdIndex < 0 || valueIndex < 0) {
                logger.warn("Required columns not found in {} (need migrated_id/r_object_id and {})",
                           csvFileName, sourceColumn);
                csvReader.close();
                return;
            }

            // Read all rows and collect values for this migrated_id
            String[] row;
            while ((row = csvReader.readNext()) != null) {
                if (row.length > Math.max(migratedIdIndex, valueIndex)) {
                    String rowMigratedId = row[migratedIdIndex].trim();
                    if (migratedId.equals(rowMigratedId)) {
                        String value = row[valueIndex].trim();
                        if (!value.isEmpty()) {
                            values.add(value);
                            logger.debug("Found {} value for migrated_id {}: {}",
                                       sourceColumn, migratedId, value);
                        }
                    }
                }
            }
            csvReader.close();
        } catch (Exception e) {
            logger.warn("Error reading {}: {}", csvFileName, e.getMessage());
            return;
        }

        // Set repeating attribute if we found any values
        if (!values.isEmpty()) {
            try {
                folderRepository.setRepeatingAttribute(folderId, targetAttribute, values);
                logger.info("Set {} value(s) for repeating attribute {} on folder with migrated_id {}",
                           values.size(), targetAttribute, migratedId);
            } catch (Exception e) {
                logger.warn("Failed to set repeating attribute {}: {}",
                           targetAttribute, e.getMessage());
            }
        } else {
            logger.debug("No {} values found for migrated_id: {}", sourceColumn, migratedId);
        }
    }

    /**
     * Convert date from DD/MM/YYYY format to MM/DD/YYYY format
     * Input: "22/04/2024, 12:00:00 AM" (DD/MM/YYYY)
     * Output: "4/22/2024, 12:00:00 AM" (MM/DD/YYYY)
     */
    private String convertDateFormat(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return dateStr;
        }

        try {
            // Parse DD/MM/YYYY format
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy, hh:mm:ss a");
            Date date = inputFormat.parse(dateStr.trim());

            // Format as MM/DD/YYYY
            SimpleDateFormat outputFormat = new SimpleDateFormat("M/d/yyyy, h:mm:ss a");
            String converted = outputFormat.format(date);
            logger.debug("Converted date from '{}' to '{}'", dateStr, converted);
            return converted;
        } catch (Exception e) {
            logger.warn("Failed to convert date format for '{}': {}. Using original value.",
                       dateStr, e.getMessage());
            return dateStr;
        }
    }
}
