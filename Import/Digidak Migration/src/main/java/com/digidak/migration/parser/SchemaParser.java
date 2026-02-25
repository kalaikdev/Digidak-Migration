package com.digidak.migration.parser;

import com.digidak.migration.model.SchemaAttribute;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Documentum schema CSV file
 */
public class SchemaParser {
    private static final Logger logger = LogManager.getLogger(SchemaParser.class);

    private static final int COL_TYPE_NAME = 4;
    private static final int COL_ATTR_NAME = 16;
    private static final int COL_LABEL_TEXT = 6;
    private static final int COL_DOMAIN_TYPE = 34;
    private static final int COL_DOMAIN_LENGTH = 35;
    private static final int COL_READ_ONLY = 26;
    private static final int COL_IS_REQUIRED = 28;

    /**
     * Parse schema file and extract attribute definitions for specific object types
     */
    public Map<String, List<SchemaAttribute>> parseSchema(String schemaFilePath) throws IOException, com.opencsv.exceptions.CsvValidationException {
        logger.info("Parsing schema file: {}", schemaFilePath);

        Map<String, List<SchemaAttribute>> schemaMap = new HashMap<>();

        CSVParser parser = new CSVParserBuilder()
                .withSeparator(';')
                .withQuoteChar('"')
                .build();

        try (CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(Files.newInputStream(Paths.get(schemaFilePath)), StandardCharsets.UTF_8))
                .withCSVParser(parser)
                .withSkipLines(1) // Skip header
                .build()) {

            String[] values;
            int rowCount = 0;

            while ((values = csvReader.readNext()) != null) {
                rowCount++;

                try {
                    if (values.length > Math.max(COL_ATTR_NAME, COL_DOMAIN_LENGTH)) {
                        String typeName = cleanValue(values[COL_TYPE_NAME]);
                        String attrName = cleanValue(values[COL_ATTR_NAME]);

                        // Filter for digidak types
                        if (typeName != null && (typeName.contains("cms_digidak_document") ||
                                typeName.contains("cms_digidak_folder"))) {

                            SchemaAttribute attribute = new SchemaAttribute();
                            attribute.setTypeName(typeName);
                            attribute.setAttrName(attrName);
                            attribute.setLabelText(cleanValue(values[COL_LABEL_TEXT]));

                            // Parse domain type
                            String domainTypeStr = cleanValue(values[COL_DOMAIN_TYPE]);
                            if (domainTypeStr != null && !domainTypeStr.isEmpty()) {
                                attribute.setDomainType(Integer.parseInt(domainTypeStr));
                            }

                            // Parse domain length
                            String domainLengthStr = cleanValue(values[COL_DOMAIN_LENGTH]);
                            if (domainLengthStr != null && !domainLengthStr.isEmpty()) {
                                attribute.setDomainLength(Integer.parseInt(domainLengthStr));
                            }

                            // Parse boolean flags
                            attribute.setReadOnly("1".equals(cleanValue(values[COL_READ_ONLY])));
                            attribute.setRequired("1".equals(cleanValue(values[COL_IS_REQUIRED])));

                            // Add to schema map
                            schemaMap.computeIfAbsent(typeName, k -> new ArrayList<>()).add(attribute);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing row {}: {}", rowCount, e.getMessage());
                }
            }
        }

        logger.info("Schema parsing completed. Found {} object types", schemaMap.size());
        schemaMap.forEach((type, attrs) ->
                logger.info("  {} -> {} attributes", type, attrs.size()));

        return schemaMap;
    }

    /**
     * Get attributes for a specific object type
     */
    public List<SchemaAttribute> getAttributesForType(Map<String, List<SchemaAttribute>> schemaMap,
                                                       String typeName) {
        return schemaMap.getOrDefault(typeName, new ArrayList<>());
    }

    /**
     * Clean CSV value by removing quotes and BOM
     */
    private String cleanValue(String value) {
        if (value == null) {
            return null;
        }
        // Remove BOM if present
        value = value.replace("\uFEFF", "");
        value = value.trim();
        return value.isEmpty() ? null : value;
    }
}
