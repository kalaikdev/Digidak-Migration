package com.digidak.migration.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Model class representing document metadata from CSV files
 */
public class DocumentMetadata {
    private String rObjectId;
    private String objectName;
    private String rObjectType;
    private String iFolderId;
    private String rFolderPath;
    private String rCreatorName;
    private Date rCreationDate;
    private String documentType;
    private String uidNumber;

    // Additional custom attributes
    private Map<String, Object> customAttributes;

    public DocumentMetadata() {
        this.customAttributes = new HashMap<>();
    }

    // Getters and Setters
    public String getrObjectId() {
        return rObjectId;
    }

    public void setrObjectId(String rObjectId) {
        this.rObjectId = rObjectId;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getrObjectType() {
        return rObjectType;
    }

    public void setrObjectType(String rObjectType) {
        this.rObjectType = rObjectType;
    }

    public String getiFolderId() {
        return iFolderId;
    }

    public void setiFolderId(String iFolderId) {
        this.iFolderId = iFolderId;
    }

    public String getrFolderPath() {
        return rFolderPath;
    }

    public void setrFolderPath(String rFolderPath) {
        this.rFolderPath = rFolderPath;
    }

    public String getrCreatorName() {
        return rCreatorName;
    }

    public void setrCreatorName(String rCreatorName) {
        this.rCreatorName = rCreatorName;
    }

    public Date getrCreationDate() {
        return rCreationDate;
    }

    public void setrCreationDate(Date rCreationDate) {
        this.rCreationDate = rCreationDate;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getUidNumber() {
        return uidNumber;
    }

    public void setUidNumber(String uidNumber) {
        this.uidNumber = uidNumber;
    }

    public Map<String, Object> getCustomAttributes() {
        return customAttributes;
    }

    public void setCustomAttributes(Map<String, Object> customAttributes) {
        this.customAttributes = customAttributes;
    }

    public void addCustomAttribute(String key, Object value) {
        this.customAttributes.put(key, value);
    }

    @Override
    public String toString() {
        return "DocumentMetadata{" +
                "objectName='" + objectName + '\'' +
                ", rObjectType='" + rObjectType + '\'' +
                ", documentType='" + documentType + '\'' +
                ", rFolderPath='" + rFolderPath + '\'' +
                '}';
    }
}
