package com.digidak.migration.service;

import com.digidak.migration.config.MigrationConfig;
import com.digidak.migration.repository.FolderRepository;
import com.digidak.migration.repository.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FolderService
 */
class FolderServiceTest {

    private FolderService folderService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private MigrationConfig config;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Create actual config for testing
        config = new MigrationConfig();
        folderRepository = new FolderRepository(null); // Mock session manager

        folderService = new FolderService(folderRepository, config);
    }

    @Test
    void testGetFolderIdByName() throws Exception {
        // Setup folder structure first
        folderService.setupFolderStructure();

        // Get folder IDs
        Map<String, String> folderIds = folderService.getAllFolderIds();

        assertNotNull(folderIds);
        assertFalse(folderIds.isEmpty());
    }

    @Test
    void testSetupFolderStructure() throws Exception {
        assertDoesNotThrow(() -> folderService.setupFolderStructure());

        Map<String, String> folderIds = folderService.getAllFolderIds();
        assertNotNull(folderIds);
    }
}
