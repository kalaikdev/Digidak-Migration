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
        System.out.println("=== ACL SERVICE === createWorkflowUserAcl called with folderId: " + folderId + ", migratedId: " + migratedId + ", users: " + userLogins);
        logger.info("=== ACL SERVICE === createWorkflowUserAcl called with folderId: {}, migratedId: {}, users: {}",
                   folderId, migratedId, userLogins);

        if (userLogins == null || userLogins.isEmpty()) {
            System.out.println("=== ACL SERVICE === No users provided for ACL creation for folder: " + folderId);
            logger.warn("=== ACL SERVICE === No users provided for ACL creation for folder: {}", folderId);
            return null;
        }

        IDfSession session = sessionManager.getSession();
        try {
            // Get folder to clone its existing ACL
            IDfFolder folder = (IDfFolder) session.getObject(new DfId(folderId));
            if (folder == null) {
                throw new Exception("Folder not found: " + folderId);
            }
            System.out.println("=== ACL SERVICE === Retrieved folder object: " + folder.getObjectName());
            logger.info("=== ACL SERVICE === Retrieved folder object: {}", folder.getObjectName());

            // Get existing ACL as template
            IDfACL existingAcl = folder.getACL();
            String aclDomain = existingAcl.getDomain();
            System.out.println("=== ACL SERVICE === Existing ACL: " + existingAcl.getObjectName() + ", domain: " + aclDomain);
            logger.info("=== ACL SERVICE === Existing ACL: {}, domain: {}",
                       existingAcl.getObjectName(), aclDomain);

            // Create new ACL
            String aclName = ACL_NAME_PREFIX + migratedId;
            System.out.println("=== ACL SERVICE === Creating new ACL with name: " + aclName);
            logger.info("=== ACL SERVICE === Creating new ACL with name: {}", aclName);

            IDfACL newAcl = (IDfACL) session.newObject("dm_acl");
            newAcl.setObjectName(aclName);
            newAcl.setDomain(aclDomain);
            newAcl.setDescription("Workflow users ACL for migrated folder " + migratedId);
            logger.info("=== ACL SERVICE === New ACL object created and configured");

            // Copy base permissions from existing ACL (dm_owner, dm_world, etc.)
            logger.info("=== ACL SERVICE === Copying base permissions from existing ACL");
            copyBasePermissions(existingAcl, newAcl, session);

            // Add workflow users with READ permission
            int addedUsers = 0;
            for (String userLogin : userLogins) {
                try {
                    logger.info("=== ACL SERVICE === Processing user: {}", userLogin);

                    // Verify user exists
                    if (!userExists(session, userLogin)) {
                        logger.warn("=== ACL SERVICE === User '{}' does not exist in dm_user, skipping", userLogin);
                        continue;
                    }
                    logger.info("=== ACL SERVICE === User '{}' exists in dm_user", userLogin);

                    // Grant READ permission (permit type 1 = user access permit)
                    logger.info("=== ACL SERVICE === Granting READ permission to user: {}", userLogin);
                    newAcl.grant(userLogin, DF_PERMIT_READ, "");

                    addedUsers++;
                    logger.info("=== ACL SERVICE === Successfully granted READ permission to user: {}", userLogin);

                } catch (Exception e) {
                    logger.error("=== ACL SERVICE === EXCEPTION: Failed to add user '{}' to ACL: {}",
                               userLogin, e.getMessage(), e);
                }
            }

            if (addedUsers == 0) {
                logger.error("=== ACL SERVICE === CRITICAL: No users were successfully added to ACL for folder: {}", folderId);
                return null;
            }

            logger.info("=== ACL SERVICE === Total users added to ACL: {}", addedUsers);

            // Save ACL
            logger.info("=== ACL SERVICE === Saving ACL object...");
            newAcl.save();
            String aclId = newAcl.getObjectId().getId();

            // Verify ACL was saved correctly
            logger.info("=== ACL SERVICE === Verifying saved ACL...");
            newAcl.fetch(null); // Refresh ACL object
            String savedAclName = newAcl.getObjectName();
            int savedAccessorCount = newAcl.getAccessorCount();

            logger.info("=== ACL SERVICE === VERIFICATION: ACL saved with object_name='{}', {} total accessors",
                       savedAclName, savedAccessorCount);

            // Log all accessors in the saved ACL
            logger.info("=== ACL SERVICE === Listing all accessors in saved ACL:");
            for (int i = 0; i < savedAccessorCount; i++) {
                String accessorName = newAcl.getAccessorName(i);
                int permitLevel = newAcl.getAccessorPermit(i);
                String accessorType = accessorName.startsWith("dm_") ? "SYSTEM" :
                                     (accessorName.contains("admin") ? "ADMIN" : "USER");
                logger.info("=== ACL SERVICE ===   [{}] {} - name='{}', permit={}",
                           i, accessorType, accessorName, permitLevel);
            }

            logger.info("=== ACL SERVICE === SUCCESS: Created ACL {} (name='{}') with {} users for folder {}",
                       aclId, savedAclName, addedUsers, folderId);

            return aclId;

        } catch (Exception e) {
            logger.error("=== ACL SERVICE === EXCEPTION in createWorkflowUserAcl: {}", e.getMessage(), e);
            throw e;
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Apply ACL to folder
     */
    public void applyAclToFolder(String folderId, String aclId) throws Exception {
        if (aclId == null || aclId.isEmpty()) {
            logger.warn("=== ACL SERVICE === No ACL ID provided for folder: {}", folderId);
            return;
        }

        IDfSession session = sessionManager.getSession();
        try {
            logger.info("=== ACL SERVICE === Applying ACL {} to folder {}", aclId, folderId);

            IDfFolder folder = (IDfFolder) session.getObject(new DfId(folderId));
            if (folder == null) {
                throw new Exception("Folder not found: " + folderId);
            }
            logger.info("=== ACL SERVICE === Folder retrieved: {} (current acl_name: {})",
                       folder.getObjectName(), folder.getACLName());

            IDfACL acl = (IDfACL) session.getObject(new DfId(aclId));
            if (acl == null) {
                throw new Exception("ACL not found: " + aclId);
            }
            logger.info("=== ACL SERVICE === ACL retrieved: {} (object_name: {})",
                       aclId, acl.getObjectName());

            // Log ACL accessor details before applying
            int accessorCount = acl.getAccessorCount();
            logger.info("=== ACL SERVICE === ACL has {} accessors before applying:", accessorCount);
            for (int i = 0; i < accessorCount; i++) {
                String accessorName = acl.getAccessorName(i);
                int permitLevel = acl.getAccessorPermit(i);
                logger.info("=== ACL SERVICE ===   Accessor {}: name='{}', permit={}",
                           i, accessorName, permitLevel);
            }

            // Set ACL on folder
            logger.info("=== ACL SERVICE === Calling folder.setACL()...");
            folder.setACL(acl);

            logger.info("=== ACL SERVICE === Saving folder...");
            folder.save();

            // Verify ACL was applied
            logger.info("=== ACL SERVICE === Verifying ACL application...");
            folder.fetch(null); // Refresh folder object
            String appliedAclName = folder.getACLName();
            String expectedAclName = acl.getObjectName();

            if (expectedAclName.equals(appliedAclName)) {
                logger.info("=== ACL SERVICE === VERIFICATION SUCCESS: Folder acl_name is now: {}", appliedAclName);
            } else {
                logger.error("=== ACL SERVICE === VERIFICATION FAILED: Expected acl_name='{}', but got '{}'",
                            expectedAclName, appliedAclName);
            }

            // Cache the ACL
            aclCache.put(folderId, aclId);

            logger.info("=== ACL SERVICE === Successfully applied ACL {} to folder {}", aclId, folderId);

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
        String dql = "SELECT user_name FROM dm_user WHERE user_name = '"
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
