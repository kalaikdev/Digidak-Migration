package com.digidak.migration.parser;

import com.digidak.migration.model.DocumentMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetadataCsvParser
 */
class MetadataCsvParserTest {

    private MetadataCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new MetadataCsvParser();
    }

    @Test
    void testParseValidMetadataFile() throws Exception {
        // Test with actual sample file
        String testFile = "DigidakMetadata_Export/digidak_single_records/4224-2024-25/document_metadata.csv";
        File file = new File(testFile);

        if (file.exists()) {
            List<DocumentMetadata> metadataList = parser.parseMetadataFile(testFile);

            assertNotNull(metadataList);
            assertFalse(metadataList.isEmpty());

            DocumentMetadata metadata = metadataList.get(0);
            assertNotNull(metadata.getObjectName());
            assertNotNull(metadata.getrObjectType());
        }
    }

    @Test
    void testParseNonExistentFile() throws Exception {
        String nonExistentFile = "non_existent_file.csv";
        List<DocumentMetadata> metadataList = parser.parseMetadataFile(nonExistentFile);

        assertNotNull(metadataList);
        assertTrue(metadataList.isEmpty());
    }

    @Test
    void testParseDirectory() throws Exception {
        String testDir = "DigidakMetadata_Export/digidak_single_records";
        File dir = new File(testDir);

        if (dir.exists()) {
            List<DocumentMetadata> metadataList = parser.parseDirectory(testDir);

            assertNotNull(metadataList);
            // Should find metadata from subdirectories
        }
    }
}
