package com.digidak.migration.repository;

import com.digidak.migration.model.FolderInfo;
import com.documentum.fc.client.IDfFolder;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.common.IDfId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real Documentum DFC Folder Repository
 * Creates actual folders in Documentum repository
 */
public class RealFolderRepository {
    private static final Logger logger = LogManager.getLogger(RealFolderRepository.class);

    private RealSessionManager sessionManager;
    private Map<String, FolderInfo> folderCache;

    public RealFolderRepository(RealSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.folderCache = new ConcurrentHashMap<>();
    }

    /**
     * Create cabinet in repository
     */
    public FolderInfo createCabinet(String cabinetName) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            logger.info("Creating cabinet: {}", cabinetName);

            String folderPath = "/" + cabinetName;

            // Check if cabinet already exists
            IDfFolder existingCabinet = session.getFolderByPath(folderPath);
            if (existingCabinet != null) {
                logger.info("Cabinet already exists: {}", cabinetName);
                FolderInfo cabinet = new FolderInfo();
                cabinet.setFolderName(cabinetName);
                cabinet.setFolderPath(folderPath);
                cabinet.setFolderType(FolderInfo.FolderType.CABINET);
                cabinet.setFolderId(existingCabinet.getObjectId().getId());
                cabinet.setAclId(existingCabinet.getACL().getObjectId().getId());
                folderCache.put(folderPath, cabinet);
                return cabinet;
            }

            // Create new cabinet (standard dm_cabinet type)
            IDfFolder cabinet = (IDfFolder) session.newObject("dm_cabinet");
            cabinet.setObjectName(cabinetName);
            cabinet.save();

            FolderInfo cabinetInfo = new FolderInfo();
            cabinetInfo.setFolderName(cabinetName);
            cabinetInfo.setFolderPath(folderPath);
            cabinetInfo.setFolderType(FolderInfo.FolderType.CABINET);
            cabinetInfo.setFolderId(cabinet.getObjectId().getId());
            cabinetInfo.setAclId(cabinet.getACL().getObjectId().getId());

            folderCache.put(folderPath, cabinetInfo);
            logger.info("Cabinet created successfully: {}", cabinetInfo);

            return cabinetInfo;
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Create folder under parent
     */
    public FolderInfo createFolder(String folderName, String parentPath,
                                    FolderInfo.FolderType folderType) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            String folderPath = parentPath + "/" + folderName;
            logger.info("Creating folder: {} under {}", folderName, parentPath);

            // Check if folder already exists
            IDfFolder existingFolder = session.getFolderByPath(folderPath);
            if (existingFolder != null) {
                logger.info("Folder already exists: {}", folderPath);
                FolderInfo folderInfo = new FolderInfo();
                folderInfo.setFolderName(folderName);
                folderInfo.setFolderPath(folderPath);
                folderInfo.setFolderType(folderType);
                folderInfo.setFolderId(existingFolder.getObjectId().getId());
                folderInfo.setAclId(existingFolder.getACL().getObjectId().getId());
                folderCache.put(folderPath, folderInfo);
                return folderInfo;
            }

            // Get parent folder
            IDfFolder parentFolder = session.getFolderByPath(parentPath);
            if (parentFolder == null) {
                throw new Exception("Parent folder not found: " + parentPath);
            }

            // Create new folder using custom type
            IDfFolder folder = (IDfFolder) session.newObject("cms_digidak_folder");
            folder.setObjectName(folderName);
            folder.link(parentFolder.getObjectId().getId());
            folder.save();

            FolderInfo folderInfo = new FolderInfo();
            folderInfo.setFolderName(folderName);
            folderInfo.setFolderPath(folderPath);
            folderInfo.setFolderType(folderType);
            folderInfo.setFolderId(folder.getObjectId().getId());
            folderInfo.setParentFolderId(parentFolder.getObjectId().getId());
            folderInfo.setAclId(folder.getACL().getObjectId().getId());

            folderCache.put(folderPath, folderInfo);
            logger.info("Folder created successfully: {}", folderInfo);

            return folderInfo;
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Get folder by path
     */
    public FolderInfo getFolderByPath(String folderPath) throws Exception {
        // Check cache first
        if (folderCache.containsKey(folderPath)) {
            return folderCache.get(folderPath);
        }

        IDfSession session = sessionManager.getSession();
        try {
            IDfFolder folder = session.getFolderByPath(folderPath);
            if (folder == null) {
                logger.warn("Folder not found: {}", folderPath);
                return null;
            }

            FolderInfo folderInfo = new FolderInfo();
            folderInfo.setFolderName(folder.getObjectName());
            folderInfo.setFolderPath(folderPath);
            folderInfo.setFolderId(folder.getObjectId().getId());
            folderInfo.setAclId(folder.getACL().getObjectId().getId());

            folderCache.put(folderPath, folderInfo);
            return folderInfo;
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Check if folder exists
     */
    public boolean folderExists(String folderPath) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            IDfFolder folder = session.getFolderByPath(folderPath);
            return folder != null;
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Get folder ACL ID
     */
    public String getFolderAcl(String folderId) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            IDfFolder folder = (IDfFolder) session.getObject(new com.documentum.fc.common.DfId(folderId));
            if (folder == null) {
                return null;
            }
            return folder.getACL().getObjectId().getId();
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Set folder metadata attributes
     */
    public void setFolderMetadata(String folderId, Map<String, Object> attributes) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            IDfFolder folder = (IDfFolder) session.getObject(new com.documentum.fc.common.DfId(folderId));
            if (folder == null) {
                throw new Exception("Folder not found: " + folderId);
            }

            // Set each attribute
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String attrName = entry.getKey();
                Object attrValue = entry.getValue();

                if (attrValue instanceof String) {
                    folder.setString(attrName, (String) attrValue);
                } else if (attrValue instanceof Boolean) {
                    folder.setBoolean(attrName, (Boolean) attrValue);
                } else if (attrValue instanceof Integer) {
                    folder.setInt(attrName, (Integer) attrValue);
                } else if (attrValue != null) {
                    folder.setString(attrName, attrValue.toString());
                }
            }

            folder.save();
            logger.debug("Folder metadata set for: {}", folderId);
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Clear cache (for testing)
     */
    public void clearCache() {
        folderCache.clear();
    }
}
