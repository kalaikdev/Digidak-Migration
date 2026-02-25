package com.digidak.migration.parser;

import com.digidak.migration.model.DocumentMetadata;
import com.digidak.migration.util.DateUtil;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for document metadata CSV files
 */
public class MetadataCsvParser {
    private static final Logger logger = LogManager.getLogger(MetadataCsvParser.class);

    // CSV column indices
    private static final int COL_R_OBJECT_ID = 0;
    private static final int COL_OBJECT_NAME = 1;
    private static final int COL_R_OBJECT_TYPE = 2;
    private static final int COL_I_FOLDER_ID = 3;
    private static final int COL_R_FOLDER_PATH = 4;
    private static final int COL_R_CREATOR_NAME = 5;
    private static final int COL_R_CREATION_DATE = 6;
    private static final int COL_DOCUMENT_TYPE = 7;

    /**
     * Parse metadata CSV file and return list of DocumentMetadata objects
     */
    public List<DocumentMetadata> parseMetadataFile(String csvFilePath) throws IOException, com.opencsv.exceptions.CsvValidationException {
        logger.debug("Parsing metadata file: {}", csvFilePath);

        Path path = Paths.get(csvFilePath);
        if (!Files.exists(path)) {
            logger.warn("Metadata file not found: {}", csvFilePath);
            return new ArrayList<>();
        }

        List<DocumentMetadata> metadataList = new ArrayList<>();

        try (CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))
                .withSkipLines(1) // Skip header
                .build()) {

            String[] values;
            int rowCount = 0;

            while ((values = csvReader.readNext()) != null) {
                rowCount++;

                try {
                    if (values.length >= 8 && !values[COL_OBJECT_NAME].trim().isEmpty()) {
                        DocumentMetadata metadata = new DocumentMetadata();

                        String rObjectId = cleanValue(values[COL_R_OBJECT_ID]);
                        String objectName = cleanValue(values[COL_OBJECT_NAME]);
                        String documentType = cleanValue(values[COL_DOCUMENT_TYPE]);

                        metadata.setrObjectId(rObjectId);
                        metadata.setObjectName(objectName);
                        metadata.setrObjectType(cleanValue(values[COL_R_OBJECT_TYPE]));
                        metadata.setiFolderId(cleanValue(values[COL_I_FOLDER_ID]));
                        metadata.setrFolderPath(cleanValue(values[COL_R_FOLDER_PATH]));
                        metadata.setrCreatorName(cleanValue(values[COL_R_CREATOR_NAME]));

                        // Parse creation date
                        String dateStr = cleanValue(values[COL_R_CREATION_DATE]);
                        if (dateStr != null) {
                            metadata.setrCreationDate(DateUtil.parseDate(dateStr));
                        }

                        metadata.setDocumentType(documentType);

                        // Per updated requirements for cms_digidak_document:
                        // object_name -> object_name (as custom attribute)
                        if (objectName != null && !objectName.isEmpty()) {
                            metadata.addCustomAttribute("object_name", objectName);
                        }
                        // category -> document_type (document_type column serves as category)
                        if (documentType != null && !documentType.isEmpty()) {
                            metadata.addCustomAttribute("document_type", documentType);
                        }
                        // title -> uid_number (no title column in CSV, would need to be added if available)
                        // r_object_id -> migrated_id
                        if (rObjectId != null && !rObjectId.isEmpty()) {
                            metadata.addCustomAttribute("migrated_id", rObjectId);
                        }
                        // Always set is_migrated=true
                        metadata.addCustomAttribute("is_migrated", true);

                        metadataList.add(metadata);
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing row {} in file {}: {}",
                            rowCount, csvFilePath, e.getMessage());
                }
            }
        }

        logger.info("Parsed {} metadata records from {}", metadataList.size(), csvFilePath);
        return metadataList;
    }

    /**
     * Parse all metadata files in a directory
     */
    public List<DocumentMetadata> parseDirectory(String directoryPath) throws IOException {
        logger.info("Parsing all metadata files in directory: {}", directoryPath);

        List<DocumentMetadata> allMetadata = new ArrayList<>();
        Path dirPath = Paths.get(directoryPath);

        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            logger.warn("Directory not found or not a directory: {}", directoryPath);
            return allMetadata;
        }

        // Find all document_metadata.csv files recursively
        Files.walk(dirPath)
                .filter(path -> path.getFileName().toString().equals("document_metadata.csv"))
                .forEach(path -> {
                    try {
                        List<DocumentMetadata> metadata = parseMetadataFile(path.toString());
                        allMetadata.addAll(metadata);
                    } catch (IOException | com.opencsv.exceptions.CsvValidationException e) {
                        logger.error("Error parsing metadata file: {}", path, e);
                    }
                });

        logger.info("Total metadata records parsed from directory: {}", allMetadata.size());
        return allMetadata;
    }

    /**
     * Clean CSV value
     */
    private String cleanValue(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }
}
