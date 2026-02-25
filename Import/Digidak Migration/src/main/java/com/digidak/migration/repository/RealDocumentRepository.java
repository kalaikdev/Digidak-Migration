package com.digidak.migration.repository;

import com.digidak.migration.model.DocumentMetadata;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.client.IDfSysObject;
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

            // Use specified object type, or default to cms_digidak_document for regular documents
            // (source system type edmapp_letter_document doesn't exist in target)
            String objectType = metadata.getrObjectType();
            if (objectType == null || objectType.trim().isEmpty() || objectType.equals("edmapp_letter_document")) {
                objectType = "cms_digidak_document";
            }
            logger.debug("Using document type: {}", objectType);

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
            // Skip owner_name here - it will be set via DQL after save to avoid ACL domain issues
            if (metadata.getCustomAttributes() != null) {
                for (Map.Entry<String, Object> entry : metadata.getCustomAttributes().entrySet()) {
                    String attrName = entry.getKey();
                    Object attrValue = entry.getValue();

                    // Skip owner_name - setting it via DFC changes acl_domain and causes
                    // DM_SYSOBJECT_E_INVALID_ACL_DOMAIN error. Will be set via DQL after save.
                    if ("owner_name".equals(attrName)) {
                        continue;
                    }

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

            IDfSysObject document = (IDfSysObject) session.getObject(new DfId(documentId));
            if (document == null) {
                throw new Exception("Document not found: " + documentId);
            }

            // Set content type
            String contentType = getContentType(contentFile.getName());
            document.setContentType(contentType);

            // Upload file content using DFC setFile method
            // DFC set_file attribute has a 255-byte UTF-8 limit, so if the file path is too long
            // (common with Hindi/Unicode filenames), copy to a temp file with a short name
            String filePath = contentFile.getAbsolutePath();
            File fileToUpload = contentFile;
            File tempFile = null;
            if (filePath.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255) {
                String extension = "";
                String name = contentFile.getName();
                int dotIndex = name.lastIndexOf('.');
                if (dotIndex >= 0) {
                    extension = name.substring(dotIndex);
                }
                tempFile = File.createTempFile("digidak_import_", extension);
                java.nio.file.Files.copy(contentFile.toPath(), tempFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                fileToUpload = tempFile;
                logger.debug("Using temp file for content upload (original path too long): {}",
                            tempFile.getAbsolutePath());
            }

            try {
                document.setFile(fileToUpload.getAbsolutePath());
            } finally {
                if (tempFile != null) {
                    tempFile.delete();
                }
            }

            logger.info("Content uploaded successfully for document: {} ({})",
                       documentId, contentFile.getName());
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
        save(documentId, null);
    }

    /**
     * Save document with optional metadata for deferred owner_name setting
     */
    public void save(String documentId, DocumentMetadata metadata) throws Exception {
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

            // Set owner_name via DQL after save to avoid ACL domain validation issues
            String ownerName = null;
            if (metadata != null && metadata.getCustomAttributes() != null) {
                Object ownerVal = metadata.getCustomAttributes().get("owner_name");
                if (ownerVal instanceof String) {
                    ownerName = (String) ownerVal;
                }
            }
            if (ownerName != null && !ownerName.trim().isEmpty()) {
                String objectType = document.getString("r_object_type");
                String dql = "UPDATE " + objectType + " OBJECTS SET owner_name = '" +
                            ownerName.replace("'", "''") + "' WHERE r_object_id = '" + documentId + "'";
                logger.debug("Setting owner_name via DQL: {}", dql);
                com.documentum.fc.client.IDfQuery query = new com.documentum.fc.client.DfQuery();
                query.setDQL(dql);
                query.execute(session, com.documentum.fc.client.IDfQuery.DF_EXEC_QUERY);
                logger.debug("owner_name set to '{}' via DQL for document: {}", ownerName, documentId);
            }
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

    /**
     * Set repeating attribute with multiple values
     * Used for attributes that can have multiple values (e.g., assigned_user)
     */
    public void setRepeatingAttribute(String documentId, String attributeName,
                                     java.util.List<String> values) throws Exception {
        IDfSession session = sessionManager.getSession();
        try {
            logger.debug("Setting repeating attribute {} for document: {}", attributeName, documentId);

            IDfSysObject document = (IDfSysObject) session.getObject(new DfId(documentId));
            if (document == null) {
                throw new Exception("Document not found: " + documentId);
            }

            // Check if attribute is actually repeating
            if (!document.isAttrRepeating(attributeName)) {
                logger.warn("Attribute {} is not defined as repeating, setting first value only", attributeName);
                if (!values.isEmpty()) {
                    document.setString(attributeName, values.get(0));
                }
                return;
            }

            // Clear existing values first
            int count = document.getValueCount(attributeName);
            for (int i = count - 1; i >= 0; i--) {
                document.remove(attributeName, i);
            }

            // Append new values
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    document.appendString(attributeName, value.trim());
                    logger.debug("Appended value to {}: {}", attributeName, value);
                }
            }

            logger.info("Set {} value(s) for repeating attribute {} on document {}",
                       values.size(), attributeName, documentId);

        } finally {
            sessionManager.releaseSession(session);
        }
    }
}
