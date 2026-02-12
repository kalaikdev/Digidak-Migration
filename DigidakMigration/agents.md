# Digidak Import Execution Order

To successfully import Digidak data into the revamp application, execute the following steps in order:

## 1. Import Digidak Single Records
**Source:** `DigidakMetadata_Export/DigidakSingleRecords_Export.csv`
- This step imports the primary Digidak folder records.
- Automatically imports associated **Movement Registers** (Step 2) from `digidak_single_records/*/movement_register.csv`.
- Automatically imports associated **Documents** (Step 3) from `digidak_single_records/*/document_metadata.csv` and content files.

## 4. Import Digidak Group Records
**Source:** `DigidakMetadata_Export/DigidakGroupRecords_Export.csv`
- This step imports Digidak Group records (folders).
- Automatically imports associated **Documents** (Step 5) from `digidak_group_records/*/document_metadata.csv` and content files.

## 6. Import Digidak Subletter Records
**Source:** `DigidakMetadata_Export/DigidakSubletterRecords_Export.csv`
- This step imports Subletter records (bulk letters).
- Automatically imports associated **Movement Registers** (Step 7) from `digidak_subletter_records/*/movement_register.csv`.

## 8. Update Repeating Attributes
**Source:** `DigidakMetadata_Export/repeating_*.csv`
- Repeating attributes (e.g., `assigned_user`, `vertical_users`) are loaded from separate CSV files.
- The import process applies these attributes during the creation/update of the Digidak objects in steps 1, 4, and 6.
- Ensure all `repeating_*.csv` files are present in the export directory before running the import.

---
**Note:** The import utility has been updated to automatically process these steps sequentially when executed via `run-import.bat`.
