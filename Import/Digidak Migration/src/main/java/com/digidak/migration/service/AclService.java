package com.digidak.migration.service;

import com.digidak.migration.repository.RealDocumentRepository;
import com.digidak.migration.repository.RealFolderRepository;
import com.digidak.migration.repository.RealSessionManager;
import com.documentum.fc.client.IDfACL;
import com.documentum.fc.client.IDfFolder;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.DfQuery;
import com.documentum.fc.common.DfId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing ACLs (Access Control Lists)
 * Handles ACL creation, permission granting, and folder application
 */
public class AclService {
    private static final Logger logger = LogManager.getLogger(AclService.class);

    // DFC Permission constants
    private static final int DF_PERMIT_READ = 3;
    private static final String ACL_NAME_PREFIX = "acl_digidak_";

    private RealFolderRepository folderRepository;
    private RealDocumentRepository documentRepository;
    private RealSessionManager sessionManager;
    private Map<String, String> aclCache; // FolderId -> AclId

    public AclService(RealFolderRepository folderRepository, RealDocumentRepository documentRepository,
                     RealSessionManager sessionManager) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.sessionManager = sessionManager;
        this.aclCache = new ConcurrentHashMap<>();
    }

    /**
     * Create custom ACL for folder with workflow users
     *
     * @param folderId Documentum folder ID
     * @param migratedId Original r_object_id (for ACL naming)
     * @param userLogins List of user login names to grant READ permission
     * @return ACL object ID
     */
    public String createWorkflowUserAcl(String folderId, String migratedId,
                                       List<String> userLogins) throws Exception {
        if (userLogins == null || userLogins.isEmpty()) {
            logger.warn("No users provided for ACL creation for folder: {}", folderId);
            return null;
        }

        IDfSession session = sessionManager.getSession();
        try {
            logger.info("Creating workflow ACL for folder {} with {} users",
                       folderId, userLogins.size());

            // Get folder to clone its existing ACL
            IDfFolder folder = (IDfFolder) session.getObject(new DfId(folderId));
            if (folder == null) {
                throw new Exception("Folder not found: " + folderId);
            }

            // Get existing ACL as template
            IDfACL existingAcl = folder.getACL();
            String aclDomain = existingAcl.getDomain();

            // Create new ACL
            String aclName = ACL_NAME_PREFIX + migratedId;
            IDfACL newAcl = (IDfACL) session.newObject("dm_acl");
            newAcl.setObjectName(aclName);
            newAcl.setDomain(aclDomain);
            newAcl.setDescription("Workflow users ACL for migrated folder " + migratedId);

            // Copy base permissions from existing ACL (dm_owner, dm_world, etc.)
            copyBasePermissions(existingAcl, newAcl, session);

            // Add workflow users with READ permission
            int addedUsers = 0;
            for (String userLogin : userLogins) {
                try {
                    // Verify user exists
                    if (!userExists(session, userLogin)) {
                        logger.warn("User '{}' does not exist, skipping", userLogin);
                        continue;
                    }

                    // Grant READ permission (permit type 1 = user access permit)
                    newAcl.grant(userLogin, DF_PERMIT_READ, "");

                    addedUsers++;
                    logger.debug("Granted READ permission to user: {}", userLogin);

                } catch (Exception e) {
                    logger.warn("Failed to add user '{}' to ACL: {}", userLogin, e.getMessage());
                }
            }

            if (addedUsers == 0) {
                logger.warn("No users were successfully added to ACL for folder: {}", folderId);
                return null;
            }

            // Save ACL
            newAcl.save();
            String aclId = newAcl.getObjectId().getId();

            logger.info("Created ACL {} with {} users for folder {}",
                       aclId, addedUsers, folderId);

            return aclId;

        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Apply ACL to folder
     */
    public void applyAclToFolder(String folderId, String aclId) throws Exception {
        if (aclId == null || aclId.isEmpty()) {
            logger.warn("No ACL ID provided for folder: {}", folderId);
            return;
        }

        IDfSession session = sessionManager.getSession();
        try {
            logger.debug("Applying ACL {} to folder {}", aclId, folderId);

            IDfFolder folder = (IDfFolder) session.getObject(new DfId(folderId));
            if (folder == null) {
                throw new Exception("Folder not found: " + folderId);
            }

            IDfACL acl = (IDfACL) session.getObject(new DfId(aclId));
            if (acl == null) {
                throw new Exception("ACL not found: " + aclId);
            }

            // Set ACL on folder
            folder.setACL(acl);
            folder.save();

            // Cache the ACL
            aclCache.put(folderId, aclId);

            logger.info("Successfully applied ACL {} to folder {}", aclId, folderId);

        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Copy base permissions from source ACL (dm_owner, dm_world, administrators, etc.)
     */
    private void copyBasePermissions(IDfACL sourceAcl, IDfACL targetAcl,
                                    IDfSession session) throws Exception {
        int permitCount = sourceAcl.getAccessorCount();

        for (int i = 0; i < permitCount; i++) {
            String accessorName = sourceAcl.getAccessorName(i);
            int permitLevel = sourceAcl.getAccessorPermit(i);
            String extendedPermit = "";

            // Copy system and base permits (dm_owner, dm_world, administrators, etc.)
            // Skip regular users as we'll add workflow users separately
            if (accessorName.startsWith("dm_") ||
                accessorName.equals("docu") ||
                accessorName.contains("admin")) {

                targetAcl.grant(accessorName, permitLevel, extendedPermit);
                logger.debug("Copied base permission for: {} (level: {})",
                            accessorName, permitLevel);
            }
        }
    }

    /**
     * Check if user exists in Documentum
     */
    private boolean userExists(IDfSession session, String userLogin) throws Exception {
        String dql = "SELECT user_login_name FROM dm_user WHERE user_login_name = '"
                     + userLogin.replace("'", "''") + "'";

        IDfQuery query = new DfQuery();
        query.setDQL(dql);

        IDfCollection collection = query.execute(session, IDfQuery.DF_READ_QUERY);
        try {
            return collection.next();
        } finally {
            collection.close();
        }
    }

    /**
     * Get ACL ID for a folder
     */
    public String getFolderAcl(String folderId) throws Exception {
        // Check cache first
        if (aclCache.containsKey(folderId)) {
            return aclCache.get(folderId);
        }

        // Get from repository
        String aclId = folderRepository.getFolderAcl(folderId);
        if (aclId != null) {
            aclCache.put(folderId, aclId);
        }

        return aclId;
    }

    /**
     * Apply ACL to document (from parent folder)
     */
    public void applyAclToDocument(String documentId, String aclId) throws Exception {
        logger.debug("Applying ACL {} to document {}", aclId, documentId);

        // In production: Use IDfSysobject.setACL()
        documentRepository.applyAcl(documentId, aclId);

        logger.debug("ACL applied successfully");
    }

    /**
     * Apply parent folder ACL to document
     */
    public void applyParentFolderAcl(String documentId, String folderId) throws Exception {
        String aclId = getFolderAcl(folderId);
        if (aclId != null) {
            applyAclToDocument(documentId, aclId);
        } else {
            logger.warn("No ACL found for folder: {}", folderId);
        }
    }

    /**
     * Create custom ACL
     * In production, would create actual dm_acl object
     */
    public String createAcl(String aclName, String domain) throws Exception {
        logger.info("Creating ACL: {} in domain: {}", aclName, domain);

        // Mock implementation
        String aclId = "45" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        logger.info("ACL created with ID: {}", aclId);
        return aclId;
    }

    /**
     * Clear ACL cache
     */
    public void clearCache() {
        aclCache.clear();
    }
}
