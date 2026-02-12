package com.digidak.migration.model;

/**
 * Model class representing a Documentum schema attribute
 */
public class SchemaAttribute {
    private String attrName;
    private String labelText;
    private String typeName;
    private int domainType;
    private int domainLength;
    private boolean isRepeating;
    private boolean isReadOnly;
    private boolean isRequired;

    public SchemaAttribute() {
    }

    public SchemaAttribute(String attrName, String typeName, int domainType) {
        this.attrName = attrName;
        this.typeName = typeName;
        this.domainType = domainType;
    }

    // Getters and Setters
    public String getAttrName() {
        return attrName;
    }

    public void setAttrName(String attrName) {
        this.attrName = attrName;
    }

    public String getLabelText() {
        return labelText;
    }

    public void setLabelText(String labelText) {
        this.labelText = labelText;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public int getDomainType() {
        return domainType;
    }

    public void setDomainType(int domainType) {
        this.domainType = domainType;
    }

    public int getDomainLength() {
        return domainLength;
    }

    public void setDomainLength(int domainLength) {
        this.domainLength = domainLength;
    }

    public boolean isRepeating() {
        return isRepeating;
    }

    public void setRepeating(boolean repeating) {
        isRepeating = repeating;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly(boolean readOnly) {
        isReadOnly = readOnly;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public void setRequired(boolean required) {
        isRequired = required;
    }

    @Override
    public String toString() {
        return "SchemaAttribute{" +
                "attrName='" + attrName + '\'' +
                ", typeName='" + typeName + '\'' +
                ", domainType=" + domainType +
                ", isRepeating=" + isRepeating +
                '}';
    }
}
