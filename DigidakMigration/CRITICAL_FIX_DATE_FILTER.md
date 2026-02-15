# CRITICAL FIX: Date Filter Mismatch Resolved

## Problem Identified ✅

**Root Cause:** The main CSV exports (Single/Group/Subletter) and repeating attribute CSV exports were using **DIFFERENT date filters**, causing r_object_id mismatch.

### Before Fix:

**Main Record Exports** (DigidakSingleRecords, DigidakGroupRecords, DigidakSubletterRecords):
```java
// HARDCODED: May 1-2, 2024
String whereClause = "(r_creation_date >= DATE('05/01/2024','mm/dd/yyyy') AND r_creation_date <= DATE('05/02/2024','mm/dd/yyyy'))";
```

**Repeating Attribute Exports** (repeating_workflow_users.csv, etc.):
```properties
# From application.properties: Year 2017
query.where.clause=DATETOSTRING("r_creation_date", 'yyyy')='2017'
```

**Result:** Completely different records exported!
- Main CSVs had folders from May 1-2, 2024
- Repeating CSVs had folders from year 2017
- **r_object_ids didn't match** → ACL couldn't link them!

---

## Solution Applied ✅

**Modified:** `DigidakExportOperation.java`

**Changes:**
1. Removed hardcoded dates from `extractSingleRecordsCSV()`, `extractGroupRecordsCSV()`, `extractSubletterRecordsCSV()`
2. All methods now accept `baseDateFilter` parameter
3. **ALL exports now use the SAME date filter** from `application.properties`

### After Fix:

```java
// Main method passes the SAME whereClause to ALL exports
String whereClause = prop.getProperty("query.where.clause"); // From application.properties

// Export Repeating Attributes (uses whereClause)
exporter.extractRepeatingAttributesCSV(session, exportPath, whereClause);

// Export Single Records (uses whereClause + additional filters)
exporter.extractSingleRecordsCSV(session, exportPath, repoName, threadCount, timeoutHours, whereClause);

// Export Group Records (uses whereClause + additional filters)
exporter.extractGroupRecordsCSV(session, exportPath, repoName, threadCount, timeoutHours, whereClause);

// Export Subletter Records (uses whereClause + additional filters)
exporter.extractSubletterRecordsCSV(session, exportPath, repoName, threadCount, timeoutHours, whereClause);
```

**Now ALL exports use the date filter from `config/application.properties`!**

---

## How to Use the Fix

### Step 1: Update application.properties

Set the **correct date filter** for your data:

```properties
# config/application.properties

# Option A: Export by year
query.where.clause=DATETOSTRING("r_creation_date", 'yyyy')='2024'

# Option B: Export by date range
query.where.clause=r_creation_date >= DATE('01/01/2024','mm/dd/yyyy') AND r_creation_date <= DATE('12/31/2024','mm/dd/yyyy')

# Option C: Export specific date range
query.where.clause=r_creation_date >= DATE('05/01/2024','mm/dd/yyyy') AND r_creation_date <= DATE('05/02/2024','mm/dd/yyyy')

# Option D: Export multiple years
query.where.clause=DATETOSTRING("r_creation_date", 'yyyy') IN ('2017','2024')
```

### Step 2: Re-run the Export

```batch
cd "D:\NB Digidak Migration\DigidakMigration"

REM Clean old export data
del /Q "D:\NB Digidak Migration\DigidakMetadata_Export\*.*"

REM Run export with fixed code
java -cp "target/classes;libs/*" com.nabard.digidak.migration.DigidakExportOperation
```

Or use the batch file:
```batch
cd "D:\NB Digidak Migration\DigidakMigration"
run-export.bat
```

### Step 3: Verify r_object_id Match

Check that the r_object_ids now match across all CSVs:

```batch
cd "D:\NB Digidak Migration\DigidakMetadata_Export"

REM Find a test r_object_id from main CSV
type DigidakSingleRecords_Export.csv | find "4225-2024-25"

REM Check if same r_object_id exists in repeating CSV
type repeating_workflow_users.csv | find "0b02cba082e13b29"
```

**Expected:** The r_object_id should be **IDENTICAL** in both files!

---

## Verification Checklist

After re-exporting:

### 1. Check Date Filter Was Applied

Look at export console output:
```
Using WHERE clause for ALL exports: DATETOSTRING("r_creation_date", 'yyyy')='2024'
========== Exporting SINGLE Records ==========
========== Exporting GROUP Records ==========
========== Exporting SUBLETTER Records ==========
```

### 2. Verify Same Record Count

All CSVs should reference the same set of folders:

```batch
REM Count unique r_object_ids in main CSVs (skip header)
findstr /V "r_object_id" DigidakSingleRecords_Export.csv | wc -l
findstr /V "r_object_id" DigidakGroupRecords_Export.csv | wc -l
findstr /V "r_object_id" DigidakSubletterRecords_Export.csv | wc -l

REM Count unique r_object_ids in repeating CSVs
cut -d, -f1 repeating_workflow_users.csv | sort | uniq | wc -l
cut -d, -f1 repeating_office_type.csv | sort | uniq | wc -l
```

The r_object_ids should all be from the same date range!

### 3. Test Specific Folder

For folder `4225-2024-25`:

```batch
REM Check in main CSV
type DigidakSingleRecords_Export.csv | findstr "4225-2024-25"
Output: 0b02cba082e13b29,4225-2024-25,...

REM Check in repeating CSV
type repeating_workflow_users.csv | findstr "0b02cba082e13b29"
Output: 0b02cba082e13b29,E Prathap
        0b02cba082e13b29,Smt Shaban Banu
```

**✅ r_object_id should match!**

---

## Impact on ACL Implementation

With this fix, the ACL implementation will now work correctly because:

1. **Main CSVs** export folders with r_object_id `0b02cba082e13b29`
2. **Repeating CSVs** export workflow users with SAME r_object_id `0b02cba082e13b29`
3. **Import process** stores r_object_id in `migrated_id` attribute
4. **ACL code** looks up workflow users using `migrated_id` → **NOW FINDS THEM!**

### Before Fix:
```
Main CSV:     r_object_id = 0c03dba083001xyz  (May 2024 folder)
Repeating CSV: r_object_id = 0b02cba082e13b29  (2017 folder)
ACL Lookup:   Can't find users for 0c03dba083001xyz → NO ACL APPLIED ❌
```

### After Fix:
```
Main CSV:     r_object_id = 0b02cba082e13b29  (2024 folder)
Repeating CSV: r_object_id = 0b02cba082e13b29  (SAME folder!)
ACL Lookup:   Finds users for 0b02cba082e13b29 → ACL APPLIED ✅
```

---

## Next Steps

1. **Update** `config/application.properties` with correct date filter
2. **Re-export** all data using fixed DigidakExportOperation
3. **Verify** r_object_ids match across all CSVs
4. **Re-run** import migration with ACL fix
5. **Verify** ACL permissions are now applied correctly

---

## Files Modified

**Source:**
- `src/main/java/com/nabard/digidak/migration/DigidakExportOperation.java`

**Compiled:**
- `target/classes/com/nabard/digidak/migration/DigidakExportOperation.class`

**Configuration:**
- `config/application.properties` (Update the query.where.clause)

---

## Summary

**Problem:** Date filter mismatch caused r_object_id mismatch between main and repeating CSVs

**Fix:** All exports now use the SAME date filter from application.properties

**Compilation:** ✅ SUCCESS (Maven build completed)

**Action Required:**
1. Update application.properties with correct date filter
2. Re-export all data
3. Re-run import migration

**Expected Result:** ACL permissions will now work because r_object_ids match!

---

**Fixed On:** 2026-02-13 16:40
**Version:** 2.0
**Status:** Ready for Re-export
