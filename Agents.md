# Digidak Migration Export Structure

**Last Updated:** February 9, 2026

## 1. Export Configuration
**Date Range:** May 1st, 2024 to May 2nd, 2024 (Inclusive)
**Base Output Path:** `C:/DigidakMigration/UAT Export Digidak/`

## 2. Export Categories & Structure

The export operation classifies records into three types, each with specific export rules:

### A. Single Records (Individual Letters)
*   **Master CSV:** `DigidakSingleRecords_Export.csv`
*   **Data Folder:** `digidak_single_records/`
*   **Included Data:**
    *   ✅ **Metadata:** Record attributes in Master CSV
    *   ✅ **Movement Register:** Detailed movement history in `movement_register.csv`
    *   ✅ **Documents:** Content files (PDF, DOCX, etc.) and `document_metadata.csv`

### B. Group Records
*   **Master CSV:** `DigidakGroupRecords_Export.csv`
*   **Data Folder:** `digidak_group_records/`
*   **Included Data:**
    *   ✅ **Metadata:** Record attributes in Master CSV
    *   ❌ **Movement Register:** **Excluded** (as per latest update)
    *   ✅ **Documents:** Content files (PDF, DOCX, etc.) and `document_metadata.csv`

### C. Subletter Records (Bulk Letters)
*   **Master CSV:** `DigidakSubletterRecords_Export.csv`
*   **Data Folder:** `digidak_subletter_records/`
*   **Included Data:**
    *   ✅ **Metadata:** Record attributes in Master CSV
    *   ✅ **Movement Register:** Detailed movement history in `movement_register.csv`
    *   ❌ **Documents:** **Excluded** (Metadata only, no content files)

## 3. Directory Layout Example

```text
C:/DigidakMigration/UAT Export Digidak/
│
├── DigidakRepeating_Export.csv           (Keywords & repeating attributes)
│
├── DigidakSingleRecords_Export.csv       (List of all Single Records)
├── digidak_single_records/
│   ├── [Record_Name_A]/
│   │   ├── movement_register.csv
│   │   ├── document_metadata.csv
│   │   └── document_1.pdf
│
├── DigidakGroupRecords_Export.csv        (List of all Group Records)
├── digidak_group_records/
│   ├── [Group_Record_Name]/
│   │   ├── document_metadata.csv
│   │   └── document_file.docx
│   │   (No movement_register.csv)
│
├── DigidakSubletterRecords_Export.csv    (List of all Subletter Records)
└── digidak_subletter_records/
    ├── [Subletter_Record_Name]/
        ├── movement_register.csv
        (No document files)
```
