# Subletter to Group Folder Mapping

## Overview

In the DigiDak Migration, subletter folders are created **INSIDE** their respective group folders, not at the cabinet level. This document explains how the parent-child relationship is determined and configured.

## Folder Hierarchy

```
/Digidak Legacy (Cabinet)
├── 4224-2024-25 (Single Record)
├── 4225-2024-25 (Single Record)
└── G65-2024-25 (Group Record)
    ├── 4245-2024-25 (Subletter) ← Created INSIDE G65-2024-25
    ├── 4246-2024-25 (Subletter) ← Created INSIDE G65-2024-25
    ├── 4247-2024-25 (Subletter) ← Created INSIDE G65-2024-25
    └── ... (more subletters)
```

## How Parent Groups Are Determined

The application uses a **3-tier priority system** to determine which group folder should contain each subletter:

### Priority 1: Metadata CSV (Highest Priority)

The application first checks the subletter's `document_metadata.csv` file to read the folder path:

```csv
r_object_id,object_name,r_object_type,i_folder_id,r_folder_path,r_creator_name,r_creation_date,document_type
xxx,document.pdf,edmapp_letter_document,xxx,/Letter/G65-2024-25/4245-2024-25,Admin,"01/05/2024",Main Letter
```

From the `r_folder_path` column (`/Letter/G65-2024-25/4245-2024-25`), the application extracts the parent folder name (`G65-2024-25`).

### Priority 2: Configuration File

If metadata is not available, the application checks `config/migration.properties` for explicit mapping:

```properties
# Subletter to Group folder mapping
subletter.4245-2024-25.parent=G65-2024-25
subletter.4246-2024-25.parent=G65-2024-25
subletter.4247-2024-25.parent=G65-2024-25
```

**To configure mapping:**
1. Edit `config/migration.properties`
2. Add entries in format: `subletter.<subletter-name>.parent=<group-name>`
3. Save the file

### Priority 3: Default Behavior (Lowest Priority)

If neither metadata nor configuration is available, the application uses the **first available group folder** found in the `digidak_group_records` directory.

**For the sample data:** All subletters will be placed under `G65-2024-25` since it's the only group folder.

## Configuration Examples

### Example 1: Single Group (Current Sample Data)

```properties
# All subletters go under G65-2024-25
subletter.4245-2024-25.parent=G65-2024-25
subletter.4246-2024-25.parent=G65-2024-25
subletter.4247-2024-25.parent=G65-2024-25
# ... (continues for all subletters)
```

**Result:**
```
/Digidak Legacy/
└── G65-2024-25/
    ├── 4245-2024-25/
    ├── 4246-2024-25/
    └── 4247-2024-25/
```

### Example 2: Multiple Groups

If you have multiple group folders:

```properties
# Subletters for Group A
subletter.4245-2024-25.parent=G65-2024-25
subletter.4246-2024-25.parent=G65-2024-25

# Subletters for Group B
subletter.5001-2024-25.parent=G70-2024-25
subletter.5002-2024-25.parent=G70-2024-25

# Subletters for Group C
subletter.6001-2024-25.parent=G80-2024-25
```

**Result:**
```
/Digidak Legacy/
├── G65-2024-25/
│   ├── 4245-2024-25/
│   └── 4246-2024-25/
├── G70-2024-25/
│   ├── 5001-2024-25/
│   └── 5002-2024-25/
└── G80-2024-25/
    └── 6001-2024-25/
```

## Code Implementation

The logic is implemented in `FolderService.java`:

```java
private String getParentGroupForSubletter(String subletterName, File subletterFolder) {
    // Priority 1: Read from metadata CSV
    String parentFromMetadata = readParentFromMetadata(subletterFolder);
    if (parentFromMetadata != null) {
        return parentFromMetadata;
    }

    // Priority 2: Check configuration
    String parentFromConfig = config.getProperty("subletter." + subletterName + ".parent");
    if (parentFromConfig != null) {
        return parentFromConfig;
    }

    // Priority 3: Use first available group folder
    return getFirstGroupFolder();
}
```

## Verification

### During Import

Check the logs to verify subletter placement:

```
[FOLDERS] Phase 3: Folder Structure Setup
   [OK] Created group record folder: G65-2024-25
   [OK] Created subletter folder: 4245-2024-25 inside group folder: G65-2024-25
   [OK] Created subletter folder: 4246-2024-25 inside group folder: G65-2024-25
```

### After Import

In Documentum Webtop or Administrator:

1. Navigate to `/Digidak Legacy`
2. Open the group folder (e.g., `G65-2024-25`)
3. Verify subletters are inside the group folder

### Using DQL

```sql
-- Check folder hierarchy
SELECT r_object_id, object_name, r_folder_path
FROM dm_folder
WHERE FOLDER('/Digidak Legacy', DESCEND)
ORDER BY r_folder_path;

-- Expected results:
-- /Digidak Legacy/G65-2024-25
-- /Digidak Legacy/G65-2024-25/4245-2024-25
-- /Digidak Legacy/G65-2024-25/4246-2024-25
```

## Troubleshooting

### Issue: Subletters created at cabinet level instead of inside group

**Cause:** Parent group folder not found or mapping incorrect

**Solution:**
1. Verify group folder exists first
2. Check configuration mapping
3. Review metadata CSV for correct paths
4. Check application logs for warnings

### Issue: Warning "Parent group folder not found"

**Cause:** Group folder hasn't been created yet or name mismatch

**Solution:**
1. Ensure group folders are created before subletters (Phase 3, Step 3 before Step 4)
2. Verify group folder names match configuration
3. Check spelling and case sensitivity

### Issue: All subletters go under wrong group

**Cause:** Configuration not loaded or metadata not readable

**Solution:**
1. Check `config/migration.properties` is in correct location
2. Verify configuration syntax
3. Ensure metadata CSV files are accessible
4. Check file encoding (should be UTF-8)

## Best Practices

### For Production Use

1. **Use Metadata (Recommended)**
   - Include correct `r_folder_path` in metadata CSV
   - This ensures accurate parent-child relationships
   - No additional configuration needed

2. **Use Configuration (Fallback)**
   - When metadata is incomplete
   - For explicit control over folder structure
   - Easy to update without modifying data

3. **Avoid Default Behavior**
   - Only for testing or demo purposes
   - May not reflect actual business relationships
   - Can cause organizational issues

### Naming Conventions

- Use consistent naming: `<ID>-<YEAR>-<NUMBER>`
- Document folder relationships in metadata
- Maintain mapping documentation

### Validation

- Always validate folder structure after import
- Run DQL queries to check hierarchy
- Review application logs for warnings
- Test with sample data first

## Summary

**Key Points:**
- ✅ Subletters are created **INSIDE** group folders
- ✅ Three methods to determine parent: metadata, config, default
- ✅ Metadata is the preferred method for production
- ✅ Configuration provides explicit control
- ✅ Default behavior uses first available group

**Current Configuration:**
- All 13 subletters → G65-2024-25 group folder
- Mapping defined in `config/migration.properties`
- Can be overridden by metadata CSV

---

**Last Updated:** February 11, 2026
**Related Files:**
- `src/main/java/com/digidak/migration/service/FolderService.java`
- `config/migration.properties`
- `README.md`
