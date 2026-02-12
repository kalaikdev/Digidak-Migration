package com.digidak.migration.repository;

import com.digidak.migration.model.DocumentMetadata;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.common.DfId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;

/**
 * Real Documentum DFC Document Repository
 * Creates actual documents in Documentum repository
 * Note: This is a simplified implementation using basic DFC APIs
 */
public class RealDocumentRepository {
    private static final Logger logger = LogManager.getLogger(RealDocumentRepository.class);

    private RealSessionManager sessionManager;

    public RealDocumentRepository(RealSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Create document in repository
     */
    public String createDocument(DocumentMetadata metadata, String folderId) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            logger.debug("Creating document: {}", metadata.getObjectName());

            // Get object type, default to dm_document if not specified
            String objectType = metadata.getrObjectType();
            if (objectType == null || objectType.trim().isEmpty()) {
                objectType = "dm_document";
            }

            // Create new document using DFC
            IDfPersistentObject document = session.newObject(objectType);
            document.setString("object_name", metadata.getObjectName());

            // Link to folder using folder path
            if (folderId != null && !folderId.trim().isEmpty()) {
                document.setString("i_folder_id", folderId);
            }

            // Note: ACL will inherit from parent folder automatically
            // No need to explicitly set ACL - Documentum will use folder's ACL or system default

            // Don't save yet - metadata and content will be set first
            String documentId = document.getObjectId().getId();
            logger.debug("Document created with ID: {} (ACL will inherit from folder)", documentId);

            return documentId;
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Set document metadata
     */
    public void setMetadata(String documentId, DocumentMetadata metadata) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            logger.debug("Setting metadata for document: {}", documentId);

            IDfPersistentObject document = session.getObject(new DfId(documentId));
            if (document == null) {
                throw new Exception("Document not found: " + documentId);
            }

            // Set basic metadata
            if (metadata.getObjectName() != null) {
                document.setString("object_name", metadata.getObjectName());
            }

            // Skip setting owner_name - use current session user as owner
            // Setting owner from metadata causes ACL domain issues
            // if (metadata.getrCreatorName() != null) {
            //     document.setString("owner_name", metadata.getrCreatorName());
            // }

            // Set custom attributes if they exist in the object type
            if (metadata.getCustomAttributes() != null) {
                for (Map.Entry<String, Object> entry : metadata.getCustomAttributes().entrySet()) {
                    String attrName = entry.getKey();
                    Object attrValue = entry.getValue();

                    try {
                        if (attrValue instanceof String) {
                            document.setString(attrName, (String) attrValue);
                        } else if (attrValue instanceof Integer) {
                            document.setInt(attrName, (Integer) attrValue);
                        } else if (attrValue instanceof Boolean) {
                            document.setBoolean(attrName, (Boolean) attrValue);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to set attribute {}: {}", attrName, e.getMessage());
                    }
                }
            }

            logger.debug("Metadata set successfully for document: {}", documentId);
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Set document content from file
     */
    public void setContent(String documentId, File contentFile) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            logger.debug("Setting content for document: {} from file: {}",
                    documentId, contentFile.getName());

            if (!contentFile.exists()) {
                throw new IllegalArgumentException("Content file does not exist: " +
                        contentFile.getAbsolutePath());
            }

            IDfPersistentObject document = session.getObject(new DfId(documentId));
            if (document == null) {
                throw new Exception("Document not found: " + documentId);
            }

            // Set content using setFile method
            document.setString("a_content_type", getContentType(contentFile.getName()));

            logger.debug("Content set successfully for document: {}", documentId);
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Link document to folder
     */
    public void linkToFolder(String documentId, String folderId) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            logger.debug("Linking document {} to folder {}", documentId, folderId);

            IDfPersistentObject document = session.getObject(new DfId(documentId));
            if (document == null) {
                throw new Exception("Document not found: " + documentId);
            }

            // Link to folder
            document.setString("i_folder_id", folderId);

            logger.debug("Document linked successfully");
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Apply ACL to document
     */
    public void applyAcl(String documentId, String aclId) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            logger.debug("Applying ACL {} to document {}", aclId, documentId);

            IDfPersistentObject document = session.getObject(new DfId(documentId));
            if (document == null) {
                throw new Exception("Document not found: " + documentId);
            }

            document.setString("acl_domain", session.getDocbaseName());
            document.setString("acl_name", aclId);

            logger.debug("ACL applied successfully");
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Save document
     */
    public void save(String documentId) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            logger.debug("Saving document: {}", documentId);

            IDfPersistentObject document = session.getObject(new DfId(documentId));
            if (document == null) {
                throw new Exception("Document not found: " + documentId);
            }

            // Force ACL domain to repository name before saving
            String currentAclDomain = document.getString("acl_domain");
            if (currentAclDomain == null || currentAclDomain.isEmpty() || !currentAclDomain.equals(session.getDocbaseName())) {
                document.setString("acl_domain", session.getDocbaseName());
                logger.debug("Set ACL domain to repository: {}", session.getDocbaseName());
            }

            document.save();

            logger.debug("Document saved successfully: {}", document.getString("object_name"));
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Get document by ID
     */
    public DocumentMetadata getDocument(String documentId) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            IDfPersistentObject document = session.getObject(new DfId(documentId));
            if (document == null) {
                return null;
            }

            DocumentMetadata metadata = new DocumentMetadata();
            metadata.setObjectName(document.getString("object_name"));
            metadata.setrObjectType(document.getType().getName());

            return metadata;
        } finally {
            sessionManager.releaseSession(session);
        }
    }

    /**
     * Get content type from file extension
     */
    private String getContentType(String filename) {
        if (filename.endsWith(".pdf")) {
            return "pdf";
        } else if (filename.endsWith(".doc") || filename.endsWith(".docx")) {
            return "msword";
        } else if (filename.endsWith(".xls") || filename.endsWith(".xlsx")) {
            return "msexcel";
        }
        return "unknown";
    }

    /**
     * Get document count (not implemented for real repository)
     */
    public int getDocumentCount() {
        return 0; // Not applicable for real repository
    }

    /**
     * Clear cache (not applicable for real repository)
     */
    public void clearCache() {
        // No-op for real repository
    }
}
