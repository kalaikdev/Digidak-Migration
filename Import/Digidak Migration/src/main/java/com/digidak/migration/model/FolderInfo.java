package com.digidak.migration.model;

/**
 * Model class representing folder information
 */
public class FolderInfo {
    private String folderId;
    private String folderName;
    private String folderPath;
    private String parentFolderId;
    private FolderType folderType;
    private String aclId;

    public enum FolderType {
        CABINET,
        SINGLE_RECORD,
        GROUP_RECORD,
        SUBLETTER_RECORD
    }

    public FolderInfo() {
    }

    public FolderInfo(String folderName, String folderPath, FolderType folderType) {
        this.folderName = folderName;
        this.folderPath = folderPath;
        this.folderType = folderType;
    }

    // Getters and Setters
    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(String parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    public FolderType getFolderType() {
        return folderType;
    }

    public void setFolderType(FolderType folderType) {
        this.folderType = folderType;
    }

    public String getAclId() {
        return aclId;
    }

    public void setAclId(String aclId) {
        this.aclId = aclId;
    }

    @Override
    public String toString() {
        return "FolderInfo{" +
                "folderName='" + folderName + '\'' +
                ", folderPath='" + folderPath + '\'' +
                ", folderType=" + folderType +
                ", folderId='" + folderId + '\'' +
                '}';
    }
}
