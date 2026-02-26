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
    private static final String ACL_NAME_PREFIX_GROUP = "acl_digidakG_";

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
     * Create custom ACL for folder with workflow users AND apply it to the folder
     * in a SINGLE session to avoid stale object / version conflict issues.
     *
     * @param folderId Documentum folder ID
     * @param migratedId Original r_object_id (for ACL naming)
     * @param userLogins List of user login names to grant READ permission
     * @param isGroupFolder If true, uses acl_digidakG_ prefix instead of acl_digidak_
     * @return ACL object ID
     */
    public String createWorkflowUserAcl(String folderId, String migratedId,
                                       List<String> userLogins, boolean isGroupFolder) throws Exception {
        System.out.println("=== ACL SERVICE === createWorkflowUserAcl called with folderId: " + folderId + ", migratedId: " + migratedId + ", isGroupFolder: " + isGroupFolder + ", users: " + userLogins);
        logger.info("=== ACL SERVICE === createWorkflowUserAcl called with folderId: {}, migratedId: {}, isGroupFolder: {}, users: {}",
                   folderId, migratedId, isGroupFolder, userLogins);

        if (userLogins == null) {
            userLogins = new java.util.ArrayList<>();
        }
        logger.info("=== ACL SERVICE === Creating custom ACL for folder: {} with {} workflow users", folderId, userLogins.size());

        IDfSession session = sessionManager.getSession();
        try {
            // Step 1: Get folder and refresh it to get latest state after repeating attribute saves
            IDfFolder folder = (IDfFolder) session.getObject(new DfId(folderId));
            if (folder == null) {
                throw new Exception("Folder not found: " + folderId);
            }
            folder.fetch(null); // CRITICAL: refresh folder to get latest state after setRepeatingAttribute saves
            System.out.println("=== ACL SERVICE === Retrieved and refreshed folder object: " + folder.getObjectName());
            logger.info("=== ACL SERVICE === Retrieved and refreshed folder object: {}", folder.getObjectName());

            // Step 2: Capture base permissions from folder's current ACL BEFORE any modifications
            IDfACL folderAcl = folder.getACL();
            String aclDomain = folderAcl.getDomain();
            String folderAclName = folderAcl.getObjectName();
            System.out.println("=== ACL SERVICE === Existing ACL: " + folderAclName + ", domain: " + aclDomain);
            logger.info("=== ACL SERVICE === Existing ACL: {}, domain: {}", folderAclName, aclDomain);

            java.util.List<String[]> basePermissions = new java.util.ArrayList<>();
            int baseCount = folderAcl.getAccessorCount();
            for (int i = 0; i < baseCount; i++) {
                String accessorName = folderAcl.getAccessorName(i);
                int permitLevel = folderAcl.getAccessorPermit(i);
                // Only copy system/admin base permissions, skip regular users like "Shaji K V"
                if (accessorName.startsWith("dm_") ||
                    accessorName.equals("docu") ||
                    accessorName.contains("admin")) {
                    basePermissions.add(new String[]{accessorName, String.valueOf(permitLevel)});
                }
            }
            logger.info("=== ACL SERVICE === Captured {} base permissions from folder ACL", basePermissions.size());

            // Step 3: Create or find existing ACL
            String aclName = (isGroupFolder ? ACL_NAME_PREFIX_GROUP : ACL_NAME_PREFIX) + migratedId;
            IDfACL newAcl = findExistingAcl(session, aclName, aclDomain);

            if (newAcl != null) {
                // Reuse existing ACL - clear old accessors and rebuild
                logger.info("=== ACL SERVICE === Found existing ACL '{}', will update it", aclName);
                int existingCount = newAcl.getAccessorCount();
                for (int i = existingCount - 1; i >= 0; i--) {
                    try {
                        newAcl.revoke(newAcl.getAccessorName(i), "");
                    } catch (Exception e) {
                        logger.warn("=== ACL SERVICE === Failed to revoke accessor at index {}: {}", i, e.getMessage());
                    }
                }
            } else {
                // Create new ACL
                logger.info("=== ACL SERVICE === Creating new ACL with name: {}", aclName);
                newAcl = (IDfACL) session.newObject("dm_acl");
                newAcl.setObjectName(aclName);
                newAcl.setDomain(aclDomain);
                newAcl.setDescription("Workflow users ACL for migrated folder " + migratedId);
            }

            // Step 4: Apply base permissions
            logger.info("=== ACL SERVICE === Applying {} base permissions to new ACL", basePermissions.size());
            for (String[] perm : basePermissions) {
                try {
                    newAcl.grant(perm[0], Integer.parseInt(perm[1]), "");
                    logger.debug("=== ACL SERVICE === Granted base permission: {} (level: {})", perm[0], perm[1]);
                } catch (Exception e) {
                    logger.warn("=== ACL SERVICE === Failed to grant base permission for {}: {}", perm[0], e.getMessage());
                }
            }

            // Step 5: Validate workflow users exist BEFORE granting to prevent ACL corruption
            java.util.List<String> validUsers = new java.util.ArrayList<>();
            for (String userLogin : userLogins) {
                try {
                    if (userExists(session, userLogin)) {
                        validUsers.add(userLogin);
                        logger.info("=== ACL SERVICE === User '{}' exists in Documentum", userLogin);
                    } else {
                        System.out.println("=== ACL SERVICE === WARNING: User '" + userLogin + "' NOT FOUND in Documentum, skipping");
                        logger.warn("=== ACL SERVICE === User '{}' NOT FOUND in Documentum, skipping", userLogin);
                    }
                } catch (Exception e) {
                    logger.warn("=== ACL SERVICE === Error checking user '{}': {}, will try granting anyway", userLogin, e.getMessage());
                    validUsers.add(userLogin); // Include if check fails - let grant() decide
                }
            }

            // Step 6: Add validated workflow users with READ permission
            int addedUsers = 0;
            for (String userLogin : validUsers) {
                try {
                    newAcl.grant(userLogin, DF_PERMIT_READ, "");
                    addedUsers++;
                    logger.info("=== ACL SERVICE === Granted READ permission to user: {}", userLogin);
                } catch (Exception e) {
                    System.out.println("=== ACL SERVICE === FAILED to grant user '" + userLogin + "': " + e.getMessage());
                    logger.error("=== ACL SERVICE === Failed to grant user '{}': {}", userLogin, e.getMessage());
                }
            }

            logger.info("=== ACL SERVICE === Added {} out of {} users to ACL", addedUsers, validUsers.size());

            // Step 7: Save ACL
            logger.info("=== ACL SERVICE === Saving ACL object...");
            newAcl.save();
            String aclId = newAcl.getObjectId().getId();
            logger.info("=== ACL SERVICE === ACL saved with id: {}, name: {}", aclId, aclName);

            // Step 8: Apply ACL to folder - try setACLDomain/setACLName first (more reliable),
            // fall back to setACL(object) if that fails
            logger.info("=== ACL SERVICE === Applying ACL '{}' to folder...", aclName);
            boolean applied = false;

            // Approach 1: setACLDomain + setACLName (bypasses DFC object cache issues)
            try {
                folder.fetch(null);
                folder.setACLDomain(aclDomain);
                folder.setACLName(aclName);
                folder.save();
                applied = true;
                logger.info("=== ACL SERVICE === Applied ACL via setACLDomain/setACLName");
            } catch (Exception e1) {
                System.out.println("=== ACL SERVICE === setACLDomain/setACLName failed: " + e1.getMessage() + ", trying setACL(object)...");
                logger.warn("=== ACL SERVICE === setACLDomain/setACLName failed: {}, trying setACL(object)...", e1.getMessage());

                // Approach 2: setACL(IDfACL) with fresh folder fetch
                try {
                    folder.fetch(null);
                    folder.setACL(newAcl);
                    folder.save();
                    applied = true;
                    logger.info("=== ACL SERVICE === Applied ACL via setACL(object)");
                } catch (Exception e2) {
                    System.out.println("=== ACL SERVICE === setACL(object) also failed: " + e2.getMessage() + ", trying DQL update...");
                    logger.warn("=== ACL SERVICE === setACL(object) also failed: {}, trying DQL update...", e2.getMessage());

                    // Approach 3: Direct DQL update as last resort
                    try {
                        String updateDql = "UPDATE dm_folder OBJECT "
                                + "SET acl_domain = '" + aclDomain.replace("'", "''") + "', "
                                + "acl_name = '" + aclName.replace("'", "''") + "' "
                                + "WHERE r_object_id = '" + folderId + "'";
                        IDfQuery updateQuery = new DfQuery();
                        updateQuery.setDQL(updateDql);
                        IDfCollection updateResult = updateQuery.execute(session, IDfQuery.DF_QUERY);
                        if (updateResult != null) {
                            updateResult.close();
                        }
                        applied = true;
                        logger.info("=== ACL SERVICE === Applied ACL via DQL update");
                    } catch (Exception e3) {
                        System.out.println("=== ACL SERVICE === ALL 3 APPROACHES FAILED for folder " + folderId + ": " + e3.getMessage());
                        logger.error("=== ACL SERVICE === ALL 3 APPROACHES FAILED for folder {}: approach1={}, approach2={}, approach3={}",
                                   folderId, e1.getMessage(), e2.getMessage(), e3.getMessage());
                    }
                }
            }

            // Verify ACL was applied
            if (applied) {
                folder.fetch(null);
                String appliedAclName = folder.getACLName();
                if (aclName.equals(appliedAclName)) {
                    System.out.println("=== ACL SERVICE === SUCCESS: Folder " + folder.getObjectName() + " acl_name is now: " + appliedAclName);
                    logger.info("=== ACL SERVICE === VERIFICATION SUCCESS: Folder acl_name is now: {}", appliedAclName);
                } else {
                    System.out.println("=== ACL SERVICE === WARNING: Expected acl_name='" + aclName + "', but got '" + appliedAclName + "'");
                    logger.error("=== ACL SERVICE === VERIFICATION FAILED: Expected acl_name='{}', but got '{}'",
                                aclName, appliedAclName);
                }
            }

            // Cache the ACL
            aclCache.put(folderId, aclId);

            logger.info("=== ACL SERVICE === Done: ACL {} (name='{}') with {} users for folder {}",
                       aclId, aclName, addedUsers, folderId);

            return aclId;

        } catch (Exception e) {
            System.out.println("=== ACL SERVICE === EXCEPTION in createWorkflowUserAcl for folder " + folderId + ": " + e.getMessage());
            e.printStackTrace();
            logger.error("=== ACL SERVICE === EXCEPTION in createWorkflowUserAcl: {}", e.getMessage(), e);
            throw e;
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Apply ACL to folder (standalone - used for other purposes)
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
            folder.fetch(null); // Refresh to get latest state

            IDfACL acl = (IDfACL) session.getObject(new DfId(aclId));
            if (acl == null) {
                throw new Exception("ACL not found: " + aclId);
            }

            folder.setACL(acl);
            folder.save();

            // Verify
            folder.fetch(null);
            String appliedAclName = folder.getACLName();
            logger.info("=== ACL SERVICE === Applied ACL to folder. acl_name is now: {}", appliedAclName);

            aclCache.put(folderId, aclId);

        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Find existing ACL by name and domain
     */
    private IDfACL findExistingAcl(IDfSession session, String aclName, String aclDomain) {
        try {
            String dql = "SELECT r_object_id FROM dm_acl WHERE object_name = '"
                         + aclName.replace("'", "''") + "' AND domain = '"
                         + aclDomain.replace("'", "''") + "'";

            IDfQuery query = new DfQuery();
            query.setDQL(dql);

            IDfCollection collection = query.execute(session, IDfQuery.DF_READ_QUERY);
            try {
                if (collection.next()) {
                    String aclId = collection.getString("r_object_id");
                    IDfACL acl = (IDfACL) session.getObject(new DfId(aclId));
                    logger.info("=== ACL SERVICE === Found existing ACL: {} (id: {})", aclName, aclId);
                    return acl;
                }
            } finally {
                collection.close();
            }
        } catch (Exception e) {
            logger.warn("=== ACL SERVICE === Error looking up existing ACL '{}': {}", aclName, e.getMessage());
        }
        return null;
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
        if (aclCache.containsKey(folderId)) {
            return aclCache.get(folderId);
        }

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
     */
    public String createAcl(String aclName, String domain) throws Exception {
        logger.info("Creating ACL: {} in domain: {}", aclName, domain);
        String aclId = "45" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        logger.info("ACL created with ID: {}", aclId);
        return aclId;
    }

    /**
     * Apply a pre-existing ACL to a folder without adding any workflow group permissions.
     * The ACL is assumed to be already pre-configured with all necessary permissions.
     *
     * @param folderId Documentum folder ID
     * @param aclName Name of the existing ACL (e.g., "ecm_legacy_digidak")
     * @return ACL object ID, or null on failure
     */
    public String applyExistingAcl(String folderId, String aclName) throws Exception {
        return applyExistingAcl(folderId, aclName, new java.util.ArrayList<>());
    }

    /**
     * Apply a pre-existing ACL to a folder and grant READ permission to a workflow group.
     *
     * @param folderId Documentum folder ID
     * @param aclName Name of the existing ACL
     * @param workflowGroupName Group to grant READ permission
     * @return ACL object ID, or null on failure
     */
    public String applyExistingAcl(String folderId, String aclName, String workflowGroupName) throws Exception {
        List<String> groups = new java.util.ArrayList<>();
        if (workflowGroupName != null && !workflowGroupName.trim().isEmpty()) {
            groups.add(workflowGroupName);
        }
        return applyExistingAcl(folderId, aclName, groups);
    }

    /**
     * Apply a pre-existing ACL to a folder and grant READ permission to workflow groups.
     * Supports multiple workflow group names (used for group folders with subletter groups).
     */
    public String applyExistingAcl(String folderId, String aclName, List<String> workflowGroupNames) throws Exception {
        System.out.println("=== ACL SERVICE === applyExistingAcl called: folderId=" + folderId + ", aclName=" + aclName + ", workflowGroups=" + workflowGroupNames);
        logger.info("=== ACL SERVICE === applyExistingAcl: folderId={}, aclName={}, workflowGroups={}", folderId, aclName, workflowGroupNames);

        IDfSession session = sessionManager.getSession();
        try {
            // Step 1: Get and refresh folder
            IDfFolder folder = (IDfFolder) session.getObject(new DfId(folderId));
            if (folder == null) {
                throw new Exception("Folder not found: " + folderId);
            }
            folder.fetch(null);

            // Step 2: Find the existing ACL by name (search without domain restriction)
            String aclDomain = null;
            IDfACL existingAcl = null;
            String findDql = "SELECT r_object_id FROM dm_acl WHERE object_name = '"
                            + aclName.replace("'", "''") + "'";
            System.out.println("=== ACL SERVICE === Searching for ACL with DQL: " + findDql);
            IDfQuery findQuery = new DfQuery();
            findQuery.setDQL(findDql);
            IDfCollection findResult = findQuery.execute(session, IDfQuery.DF_READ_QUERY);
            try {
                if (findResult.next()) {
                    String aclObjId = findResult.getString("r_object_id");
                    existingAcl = (IDfACL) session.getObject(new DfId(aclObjId));
                    aclDomain = existingAcl.getDomain();
                    System.out.println("=== ACL SERVICE === Found ACL: id=" + aclObjId + ", domain=" + aclDomain);
                }
            } finally {
                findResult.close();
            }

            if (existingAcl == null) {
                System.out.println("=== ACL SERVICE === ERROR: ACL '" + aclName + "' not found in repository!");
                logger.error("=== ACL SERVICE === ACL '{}' not found in repository", aclName);
                return null;
            }

            String aclId = existingAcl.getObjectId().getId();
            System.out.println("=== ACL SERVICE === Found existing ACL: id=" + aclId + ", domain=" + aclDomain);

            // Step 3: Grant READ permission to each workflow group
            for (String groupName : workflowGroupNames) {
                if (groupName == null || groupName.trim().isEmpty()) continue;
                boolean groupExists = userExists(session, groupName);
                if (groupExists) {
                    try {
                        existingAcl.grant(groupName, DF_PERMIT_READ, "");
                        System.out.println("=== ACL SERVICE === Granted READ to '" + groupName + "' on ACL '" + aclName + "'");
                    } catch (Exception e) {
                        System.out.println("=== ACL SERVICE === Failed to grant '" + groupName + "': " + e.getMessage());
                    }
                } else {
                    System.out.println("=== ACL SERVICE === WARNING: Group/user '" + groupName + "' does not exist, skipping grant");
                }
            }
            if (!workflowGroupNames.isEmpty()) {
                existingAcl.save();
            }

            // Step 4: Apply ACL to folder using 3-tier fallback
            boolean applied = false;

            // Approach 1: setACLDomain + setACLName
            try {
                folder.fetch(null);
                folder.setACLDomain(aclDomain);
                folder.setACLName(aclName);
                folder.save();
                applied = true;
                System.out.println("=== ACL SERVICE === Applied existing ACL via setACLDomain/setACLName");
            } catch (Exception e1) {
                System.out.println("=== ACL SERVICE === setACLDomain/setACLName failed: " + e1.getMessage() + ", trying setACL(object)...");

                // Approach 2: setACL(IDfACL)
                try {
                    folder.fetch(null);
                    folder.setACL(existingAcl);
                    folder.save();
                    applied = true;
                    System.out.println("=== ACL SERVICE === Applied existing ACL via setACL(object)");
                } catch (Exception e2) {
                    System.out.println("=== ACL SERVICE === setACL(object) failed: " + e2.getMessage() + ", trying DQL...");

                    // Approach 3: DQL UPDATE
                    try {
                        String updateDql = "UPDATE dm_folder OBJECT "
                                + "SET acl_domain = '" + aclDomain.replace("'", "''") + "', "
                                + "acl_name = '" + aclName.replace("'", "''") + "' "
                                + "WHERE r_object_id = '" + folderId + "'";
                        IDfQuery updateQuery = new DfQuery();
                        updateQuery.setDQL(updateDql);
                        IDfCollection updateResult = updateQuery.execute(session, IDfQuery.DF_QUERY);
                        if (updateResult != null) updateResult.close();
                        applied = true;
                        System.out.println("=== ACL SERVICE === Applied existing ACL via DQL update");
                    } catch (Exception e3) {
                        System.out.println("=== ACL SERVICE === ALL 3 APPROACHES FAILED for folder " + folderId);
                        logger.error("=== ACL SERVICE === ALL 3 APPROACHES FAILED: {}, {}, {}", e1.getMessage(), e2.getMessage(), e3.getMessage());
                    }
                }
            }

            // Verify
            if (applied) {
                folder.fetch(null);
                String appliedAclName = folder.getACLName();
                if (aclName.equals(appliedAclName)) {
                    System.out.println("=== ACL SERVICE === SUCCESS: Folder " + folder.getObjectName() + " acl_name is now: " + appliedAclName);
                } else {
                    System.out.println("=== ACL SERVICE === WARNING: Expected acl_name='" + aclName + "', but got '" + appliedAclName + "'");
                }
            }

            aclCache.put(folderId, aclId);
            return aclId;

        } catch (Exception e) {
            System.out.println("=== ACL SERVICE === EXCEPTION in applyExistingAcl for folder " + folderId + ": " + e.getMessage());
            e.printStackTrace();
            logger.error("=== ACL SERVICE === EXCEPTION in applyExistingAcl: {}", e.getMessage(), e);
            throw e;
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Clear ACL cache
     */
    public void clearCache() {
        aclCache.clear();
    }
}
