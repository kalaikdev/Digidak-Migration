# DigiDak Migration - Batch File Guide

## Available Batch Files

### 1. `run_migration.bat` ⭐ **RECOMMENDED**
**Complete migration with compilation**

```bash
run_migration.bat
```

**What it does:**
- ✅ Compiles all source files
- ✅ Compiles phase runners
- ✅ Executes Phase 1 (Folder Structure)
- ✅ Executes Phase 2 (Document Import)
- ✅ Executes Phase 3 (Movement Registers)
- ✅ Shows detailed progress and error handling

**Duration:** ~40 seconds (6s compile + 34s execution)

**Use when:** Running migration for the first time or after code changes

---

### 2. `run_migration_quick.bat`
**Quick execution without recompiling**

```bash
run_migration_quick.bat
```

**What it does:**
- ✅ Executes Phase 1, 2, 3 in sequence
- ⚠️ Skips compilation (assumes already compiled)

**Duration:** ~34 seconds

**Use when:** Re-running migration without code changes

---

### 3. `compile_only.bat`
**Compile without executing**

```bash
compile_only.bat
```

**What it does:**
- ✅ Compiles all source files
- ✅ Compiles phase runners
- ❌ Does not execute phases

**Duration:** ~6 seconds

**Use when:** Testing if code compiles without running migration

---

## Individual Phase Runners

If you need to run phases separately, you can use Java directly:

### Phase 1 Only (Folder Structure)
```bash
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED -cp "libs/*;." Phase1Runner
```

### Phase 2 Only (Document Import)
```bash
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED -cp "libs/*;." Phase2Runner
```

### Phase 3 Only (Movement Registers)
```bash
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED -cp "libs/*;." Phase3Runner
```

---

## Expected Results

| Phase | Objects Created | Metadata Attributes | Duration |
|-------|----------------|-------------------|----------|
| Phase 1 | 7 folders | 26 per folder | ~16s |
| Phase 2 | 5 documents | 4 per document | ~9s |
| Phase 3 | 15 movement registers | 7 per register | ~9s |
| **TOTAL** | **27 objects** | **Full metadata** | **~34s** |

---

## Troubleshooting

### Error: "javac is not recognized"
**Solution:** Add Java bin directory to PATH
```bash
set PATH=%PATH%;C:\Program Files\Java\jdk-17\bin
```

### Error: "Cannot find libs directory"
**Solution:** Ensure you're in the correct directory
```bash
cd "c:\Workspace\Digidak Migration"
```

### Error: "DfException: Authentication failed"
**Solution:**
- Check Documentum repository connection
- Verify credentials in configuration
- Ensure NABARDUAT repository is accessible

### Error: "Folder already exists"
**Solution:** This is normal - the system will reuse existing folders

---

## Quick Start Guide

### First Time Setup
1. Ensure all dependencies are in `libs/` directory
2. Verify Documentum connection
3. Run: `run_migration.bat`

### Re-running Migration
1. If no code changes: `run_migration_quick.bat`
2. If code changed: `run_migration.bat`

### Testing Compilation Only
1. Run: `compile_only.bat`
2. Check for compilation errors

---

## Repository Structure After Migration

```
NABARDUAT Repository
└── /Digidak Legacy/
    ├── 4224-2024-25/
    │   └── 1 document
    ├── 4225-2024-25/
    │   ├── 2 documents
    │   └── 2 movement registers
    └── G65-2024-25/
        ├── 2 documents
        ├── 4245-2024-25/ (4 movement registers)
        ├── 4246-2024-25/ (5 movement registers)
        └── 4250-2024-25/ (4 movement registers)
```

---

## Metadata Implementation

### cms_digidak_folder (26 attributes)
All folders include: letter_subject, priority, uid_number, initiator, r_creation_date, mode_of_receipt, state_of_sender, decision, selected_region, is_group, languages, address_of_sender, secrecy, region, letter_no, financial_year, received_from, nature_of_correspondence, type_category, file_number, is_bulk_letter, entry_type, login_office_type, login_region, migrated_id, status=Closed, is_migrated=true

### cms_digidak_movement_re (7 attributes)
All movement registers include: status, letter_subject, completed_date, performer, type_category, r_creator_name, migrated_id, is_migrated=true

### cms_digidak_document (4 attributes)
All documents include: object_name, document_type, migrated_id, is_migrated=true

---

## Support

For issues or questions:
- Check log files in the output
- Verify repository connection
- Review error messages in console
- Check Documentum Administrator for created objects
