# ACL Permission Fix - Deployment Guide

## Problem Statement
Workflow users from `workflow_groups` attribute were not being granted folder ACL permissions. Folders were created with the repeating attribute set correctly, but users could not access the folders.

**Example:** Folder `4225-2024-25` (r_object_id: `0b02cba082e13b29`) has 2 workflow users:
- E Prathap
- Smt Shaban Banu

These users should have READ permission on the folder's ACL.

---

## Solution Implemented

### 1. User Lookup Service (NEW)
**File:** `src/main/java/com/digidak/migration/service/UserLookupService.java`

**Features:**
- Resolves display names ("E Prathap") to login names ("eprathap")
- Batch query optimization (IN clause for up to 50 users)
- Caching to avoid redundant lookups
- Fallback to normalized name patterns (firstname.lastname, flast, etc.)
- Comprehensive debug logging

**Key Method:**
```java
public Map<String, String> batchResolveUsers(List<String> displayNames)
```

### 2. Enhanced ACL Service
**File:** `src/main/java/com/digidak/migration/service/AclService.java`

**Enhanced Methods:**
```java
public String createWorkflowUserAcl(String folderId, String migratedId, List<String> userLogins)
public void applyAclToFolder(String folderId, String aclId)
```

**Features:**
- Creates custom ACL with name format: `acl_digidak_<migrated_id>`
- Copies base permissions from folder's existing ACL (dm_owner, dm_world, etc.)
- Adds workflow users with READ permission (permit level 3)
- Validates users exist in dm_user before adding
- Verifies ACL was saved and applied correctly
- Lists all accessors with their permission levels

### 3. Folder Service Integration
**File:** `src/main/java/com/digidak/migration/service/FolderService.java`

**Integration Point (line 699):**
```java
private void setRepeatingAttributes(String folderId, String migratedId) {
    // ... existing repeating attribute code ...
    setRepeatingAttribute(folderId, migratedId, "repeating_workflow_users.csv",
                         "workflow_users", "workflow_groups");

    // NEW: Apply ACL permissions for workflow users
    applyWorkflowUserAcls(folderId, migratedId);
}
```

---

## Files Modified

### New Files (1)
1. **UserLookupService.java** - User display name to login name resolution

### Modified Files (4)
1. **AclService.java** - Added ACL creation, verification, and application methods
2. **FolderService.java** - Integrated ACL application after setting workflow_groups
3. **Phase1Runner.java** - Wired up UserLookupService and AclService
4. **Phase2Runner.java** - Updated service initialization
5. **Phase3Runner.java** - Updated service initialization

---

## Deployment Steps

### Prerequisites
- Machine with network access to NABARDUAT Documentum repository
- Java 8 or higher installed
- Documentum DFC libraries in `libs/` directory

### Step 1: Deploy Code
Copy the entire `Import\Digidak Migration` directory to the target machine with Documentum connectivity.

All Java files have been compiled. If recompilation is needed:
```batch
cd "Import\Digidak Migration"

REM Compile all source files
javac -cp "libs/*;." -d . src/main/java/com/digidak/migration/util/*.java ^
  src/main/java/com/digidak/migration/model/*.java ^
  src/main/java/com/digidak/migration/config/*.java ^
  src/main/java/com/digidak/migration/repository/*.java ^
  src/main/java/com/digidak/migration/parser/*.java ^
  src/main/java/com/digidak/migration/service/*.java

REM Compile phase runners
javac -cp "libs/*;." Phase1Runner.java Phase2Runner.java Phase3Runner.java
```

### Step 2: Verify Configuration
Check `config/migration.properties` and `config/dfc.properties` have correct repository settings:
- Repository: NABARDUAT
- Docbroker host and port
- Credentials

### Step 3: Run Migration
```batch
cd "Import\Digidak Migration"
run_migration.bat
```

Or run phases individually:
```batch
REM Phase 1: Folder structure with ACL permissions
java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/sun.reflect=ALL-UNNAMED ^
     -cp "libs/*;." Phase1Runner

REM Phase 2: Document import (if needed)
java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/sun.reflect=ALL-UNNAMED ^
     -cp "libs/*;." Phase2Runner

REM Phase 3: Movement registers (if needed)
java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/sun.reflect=ALL-UNNAMED ^
     -cp "libs/*;." Phase3Runner
```

---

## Verification

### 1. Check Console Logs

**Look for ACL Creation:**
```
=== USER LOOKUP === batchResolveUsers called with 2 display names: [E Prathap, Smt Shaban Banu]
=== USER LOOKUP === Batch query returned 2 results: {E Prathap=eprathap, Smt Shaban Banu=shabanbanu}

=== ACL SERVICE === Creating new ACL with name: acl_digidak_0b02cba082e13b29
=== ACL SERVICE === Total users added to ACL: 2
=== ACL SERVICE === VERIFICATION: ACL saved with object_name='acl_digidak_0b02cba082e13b29', 4 total accessors
=== ACL SERVICE === Listing all accessors in saved ACL:
=== ACL SERVICE ===   [0] SYSTEM - name='dm_owner', permit=7
=== ACL SERVICE ===   [1] SYSTEM - name='dm_world', permit=2
=== ACL SERVICE ===   [2] USER - name='eprathap', permit=3
=== ACL SERVICE ===   [3] USER - name='shabanbanu', permit=3
```

**Look for ACL Application:**
```
=== ACL SERVICE === Applying ACL ... to folder ...
=== ACL SERVICE === Folder retrieved: 4225-2024-25 (current acl_name: old_acl_name)
=== ACL SERVICE === VERIFICATION SUCCESS: Folder acl_name is now: acl_digidak_0b02cba082e13b29
```

### 2. DQL Verification Queries

#### Check Folders Have Custom ACLs
```sql
SELECT r_object_id, object_name, acl_name
FROM cms_digidak_folder
WHERE acl_name LIKE 'acl_digidak_%'
ORDER BY r_modify_date DESC
```

#### Check ACL for Specific Folder
```sql
SELECT r_object_id, object_name, acl_name
FROM cms_digidak_folder
WHERE object_name = '4225-2024-25'
```

Expected result: `acl_name = 'acl_digidak_0b02cba082e13b29'`

#### Check ACL Permissions
```sql
SELECT accessor_name, accessor_permit
FROM dm_acl
WHERE object_name = 'acl_digidak_0b02cba082e13b29'
ORDER BY accessor_name
```

Expected results should include:
- dm_owner: 7 (Delete/Write/Read)
- dm_world: 2 (Browse/Read)
- eprathap: 3 (Read)
- shabanbanu: 3 (Read)

#### Verify User Resolution
```sql
SELECT user_name, user_login_name
FROM dm_user
WHERE user_name IN ('E Prathap', 'Smt Shaban Banu')
```

### 3. Test User Access

1. Log in to Webtop/D2 as one of the workflow users (e.g., eprathap)
2. Navigate to `/Digidak Legacy/4225-2024-25`
3. Verify you can VIEW the folder and its contents
4. Verify you CANNOT modify or delete (READ permission only)

---

## Troubleshooting

### Issue: Users Not Found in dm_user
**Log Message:** `=== USER LOOKUP === User 'E Prathap' NOT FOUND in dm_user`

**Solution:**
- Check if user exists with different display name
- Verify user_name attribute in dm_user table
- Check for typos or extra spaces in CSV file

### Issue: ACL Creation Fails
**Log Message:** `=== ACL SERVICE === CRITICAL: No users were successfully added to ACL`

**Solution:**
- Check all users exist in Documentum (userExists validation)
- Review user login names are correct
- Check for DFC permission issues

### Issue: ACL Not Applied to Folder
**Log Message:** `=== ACL SERVICE === VERIFICATION FAILED: Expected acl_name='...', but got '...'`

**Solution:**
- Check folder.setACL() succeeded
- Verify folder.save() completed
- Check for DFC transaction issues

---

## Data Summary

**Total Folders with Workflow Users:** 40 folders
**Total Workflow User Entries:** 299 entries
**Average Users per Folder:** 7.5 users
**Test Folder:** 4225-2024-25 (r_object_id: 0b02cba082e13b29)

**CSV Source:** `DigidakMetadata_Export/repeating_workflow_users.csv`

---

## Success Criteria

✅ All 40 folders created successfully
✅ Custom ACLs created with format `acl_digidak_<migrated_id>`
✅ Workflow users resolved from display names to login names
✅ Users granted READ permission on their assigned folders
✅ Users not found in Documentum logged but don't block migration
✅ Folders without workflow users use default/parent ACL
✅ Console logs show verification success for ACL application

---

## Rollback (If Needed)

If ACL implementation causes issues:

### Option 1: Remove Custom ACLs
```sql
DELETE dm_acl OBJECTS WHERE object_name LIKE 'acl_digidak_%'
```

### Option 2: Reset Folder ACLs to Default
```dql
UPDATE cms_digidak_folder OBJECTS
SET acl_name = (SELECT acl_name FROM dm_cabinet WHERE object_name = 'Digidak Legacy')
WHERE acl_name LIKE 'acl_digidak_%'
```

---

## Contact / Support

For issues or questions:
1. Check logs in `logs/digidak-migration.log`
2. Check console output for `=== ACL ===` debug messages
3. Run DQL verification queries
4. Review this deployment guide

---

**Last Updated:** 2026-02-13
**Version:** 1.0
**Status:** Ready for Production Deployment
