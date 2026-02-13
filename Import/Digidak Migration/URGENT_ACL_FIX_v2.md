# URGENT: ACL Fix v2 - Enhanced Diagnostics

## Problem Identified

You ran the migration on another machine and:
- ✅ All folders, metadata, and documents created successfully
- ❌ ACL name is `dm_4502cba08001a2e6` (default) instead of `acl_digidak_<migrated_id>`
- ❌ Workflow users not getting permissions

The ACL name format `dm_XXXXX` is the **default Documentum ACL**, which means our custom ACL code is **NOT being executed**.

---

## Root Cause Analysis

The issue is one of these:

### Issue 1: Wrong Code Deployed (MOST LIKELY)
You may have run the migration using old compiled code without the ACL fix.

### Issue 2: ACL Code Failing Silently
The ACL code is running but failing without visible errors due to logging configuration issues.

### Issue 3: Exception Before ACL Code
An exception is occurring in `setRepeatingAttributes()` before reaching the ACL code.

---

## Fix Applied - Version 2

I've added **System.out.println** statements in addition to logger calls so you'll see console output even if logging isn't configured:

### Enhanced Files:
1. **FolderService.java** - Console output at every step
2. **UserLookupService.java** - Console output for user resolution
3. **AclService.java** - Console output for ACL creation

### Expected Console Output:
```
=== ACL DEBUG === Applying workflow user ACLs for folder: 0a... (migrated_id: 0b02cba082e13b29)
=== ACL DEBUG === Read 2 workflow user names from CSV: [E Prathap, Smt Shaban Banu]
=== USER LOOKUP === batchResolveUsers called with 2 display names: [E Prathap, Smt Shaban Banu]
=== USER LOOKUP === batchResolveUsers returning 2 total results: {E Prathap=eprathap, Smt Shaban Banu=shabanbanu}
=== ACL SERVICE === createWorkflowUserAcl called with folderId: 0a..., migratedId: 0b02cba082e13b29, users: [eprathap, shabanbanu]
=== ACL SERVICE === Creating new ACL with name: acl_digidak_0b02cba082e13b29
=== ACL DEBUG === ACL created with ID: 45..., now applying to folder
=== ACL DEBUG === SUCCESS: Applied workflow ACL 45... to folder: 0a...
```

---

## Deployment Steps (CRITICAL - Follow Exactly)

### Step 1: Stop Any Running Migration
```batch
taskkill /F /IM java.exe
```

### Step 2: Copy Updated Files to Target Machine

**IMPORTANT:** You MUST copy these compiled .class files to the machine where you're running the migration:

From `d:\NB Digidak Migration\Import\Digidak Migration\`:

```
Copy these directories:
├── com/digidak/migration/service/
│   ├── FolderService.class           ← UPDATED
│   ├── UserLookupService.class       ← UPDATED
│   ├── AclService.class              ← UPDATED
│   └── [other service files]
│
├── Phase1Runner.class                ← UPDATED
├── Phase2Runner.class
├── Phase3Runner.class
│
└── libs/                             ← All DFC libraries
```

### Step 3: Verify Files Were Copied

On the target machine, check file timestamps:
```batch
dir /TC com\digidak\migration\service\FolderService.class
dir /TC com\digidak\migration\service\UserLookupService.class
dir /TC com\digidak\migration\service\AclService.class
dir /TC Phase1Runner.class
```

**All files should have TODAY's date/time (2026-02-13)**

### Step 4: Run Migration with Console Output Visible

**DO NOT** run via `run_migration.bat` - it might hide console output.

**Run Phase 1 directly:**
```batch
cd "path\to\Import\Digidak Migration"

java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/sun.reflect=ALL-UNNAMED ^
     -cp "libs/*;." Phase1Runner 2>&1 | tee console_output.txt
```

This will:
- Show all output on console
- Save output to `console_output.txt`

### Step 5: Watch for ACL Debug Messages

**IMMEDIATELY** after folders start being created, you should see:
```
=== ACL DEBUG === Applying workflow user ACLs for folder: ...
```

**If you DON'T see this message**, the old code is running!

---

## Diagnostic Scenarios

### Scenario A: No "=== ACL DEBUG ===" Messages Appear
**Problem:** Old code is still running (ACL fix not deployed)

**Solution:**
1. Verify you copied the .class files correctly
2. Check file timestamps - must be 2026-02-13
3. Delete old .class files first, then copy new ones
4. Recompile on target machine:
   ```batch
   javac -cp "libs/*;." -d . src/main/java/com/digidak/migration/service/*.java
   javac -cp "libs/*;." Phase1Runner.java
   ```

### Scenario B: "=== ACL DEBUG ===" Appears, Then "No workflow users found"
**Problem:** CSV file not found or migrated_id mismatch

**Check:**
```
=== ACL DEBUG === Read 0 workflow user names from CSV: []
```

**Solution:**
1. Verify `DigidakMetadata_Export/repeating_workflow_users.csv` exists
2. Check CSV has correct format:
   ```csv
   r_object_id,workflow_users
   0b02cba082e13b29,E Prathap
   0b02cba082e13b29,Smt Shaban Banu
   ```
3. Verify migrated_id in folder matches r_object_id in CSV

### Scenario C: "=== USER LOOKUP ===" Shows "returning 0 total results"
**Problem:** User names not found in dm_user table

**Check:**
```
=== USER LOOKUP === batchResolveUsers returning 0 total results: {}
=== ACL DEBUG === CRITICAL: No workflow users could be resolved
```

**Solution:**
Run this DQL to check user names:
```sql
SELECT user_name, user_login_name
FROM dm_user
WHERE user_name LIKE '%Prathap%'
   OR user_name LIKE '%Shaban%'
```

If users don't exist with those exact names, the CSV has wrong display names.

### Scenario D: "=== ACL SERVICE ===" Shows ACL Created, But Folder Still Has Default ACL
**Problem:** ACL created but not applied to folder

**Check:**
```
=== ACL SERVICE === Creating new ACL with name: acl_digidak_0b02cba082e13b29
=== ACL DEBUG === SUCCESS: Applied workflow ACL 45xxx to folder: 0a...
```

**Verify in DQL:**
```sql
-- Check if ACL exists
SELECT r_object_id, object_name FROM dm_acl WHERE object_name = 'acl_digidak_0b02cba082e13b29'

-- Check if folder references it
SELECT r_object_id, object_name, acl_name FROM cms_digidak_folder WHERE r_object_id = '0b02cba082e13b29'
```

If ACL exists but folder doesn't reference it, there's an issue with `folder.setACL()` or `folder.save()`.

---

## Quick Test - Single Folder

To test just one folder without running full migration:

**Create test file:** `test_acl.java`
```java
// Quick test to verify ACL code is working
// Place this in the same directory as Phase1Runner.java

import com.digidak.migration.config.*;
import com.digidak.migration.repository.*;
import com.digidak.migration.service.*;
import java.util.*;

public class test_acl {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing ACL creation...");

        // Initialize services
        DfcConfig dfcConfig = new DfcConfig("config/dfc.properties");
        RealSessionManager sessionManager = RealSessionManager.getInstance(dfcConfig);

        RealFolderRepository folderRepo = new RealFolderRepository(sessionManager);
        RealDocumentRepository docRepo = new RealDocumentRepository(sessionManager);

        UserLookupService userLookup = new UserLookupService(sessionManager);
        AclService aclService = new AclService(folderRepo, docRepo, sessionManager);

        // Test user resolution
        List<String> testUsers = Arrays.asList("E Prathap", "Smt Shaban Banu");
        System.out.println("Testing user lookup for: " + testUsers);
        Map<String, String> resolved = userLookup.batchResolveUsers(testUsers);
        System.out.println("Resolved users: " + resolved);

        // Test ACL creation (use actual folder ID from your system)
        String testFolderId = "0a02cba082001234"; // REPLACE WITH REAL FOLDER ID
        String testMigratedId = "0b02cba082e13b29";
        List<String> userLogins = new ArrayList<>(resolved.values());

        if (!userLogins.isEmpty()) {
            System.out.println("Creating ACL...");
            String aclId = aclService.createWorkflowUserAcl(testFolderId, testMigratedId, userLogins);
            System.out.println("ACL created: " + aclId);

            if (aclId != null) {
                System.out.println("Applying ACL...");
                aclService.applyAclToFolder(testFolderId, aclId);
                System.out.println("ACL applied successfully!");
            }
        }

        sessionManager.shutdown();
        System.out.println("Test complete.");
    }
}
```

**Compile and run:**
```batch
javac -cp "libs/*;." test_acl.java
java -cp "libs/*;." test_acl
```

---

## Verification Checklist

After running migration:

- [ ] Console shows `=== ACL DEBUG === Applying workflow user ACLs` for each folder
- [ ] Console shows `=== USER LOOKUP === batchResolveUsers returning X total results`
- [ ] Console shows `=== ACL SERVICE === Creating new ACL with name: acl_digidak_XXXXX`
- [ ] Console shows `=== ACL DEBUG === SUCCESS: Applied workflow ACL`
- [ ] No exceptions or stack traces in console
- [ ] `console_output.txt` file created with all output
- [ ] DQL query shows `acl_digidak_*` ACLs created
- [ ] Folders have `acl_name = 'acl_digidak_*'` instead of `dm_*`

---

## What to Send Back

After running, please send:

1. **Console output** - First 200 lines showing folder creation and ACL messages
2. **DQL query results:**
   ```sql
   SELECT r_object_id, object_name, acl_name
   FROM cms_digidak_folder
   WHERE object_name = '4225-2024-25'
   ```
3. **File timestamps** - Screenshot or output of:
   ```batch
   dir /TC com\digidak\migration\service\*.class
   ```

This will help me diagnose exactly what's happening.

---

## Emergency Rollback

If migration fails, rollback with:
```sql
-- Delete custom ACLs
DELETE dm_acl OBJECTS WHERE object_name LIKE 'acl_digidak_%'

-- Reset folder ACLs to default
UPDATE cms_digidak_folder OBJECTS
SET acl_name = (SELECT acl_name FROM dm_cabinet WHERE object_name = 'Digidak Legacy')
WHERE acl_name LIKE 'acl_digidak_%'
```

---

**Version:** 2.0
**Date:** 2026-02-13 14:30
**Critical:** Console output MUST show "=== ACL DEBUG ===" messages or old code is running!
