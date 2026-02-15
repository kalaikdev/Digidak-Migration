# Repeating Attributes Export - Fixed Approach

## Problem
The previous `ROW_BASED` query approach was producing incorrect `r_object_id` values in the exported CSV files for repeating attributes. This caused data integrity issues during migration.

## Root Cause
Using `ENABLE (ROW_BASED)` in DQL queries with repeating attributes can produce unreliable results in Documentum. The r_object_id returned may not correctly match the source object.

## Solution: Programmatic Iteration Approach

### Old Approach (Unreliable)
```sql
SELECT r_object_id, attribute_name FROM type_name WHERE ... ENABLE (ROW_BASED)
```
- Problem: ROW_BASED can return incorrect r_object_id mappings
- Risk: Data corruption during import

### New Approach (Reliable)
```java
1. Query to get object IDs only: SELECT r_object_id FROM type_name WHERE ...
2. For each object:
   a. Fetch object: session.getObject(objectId)
   b. Get value count: object.getValueCount(attributeName)
   c. Loop through values: object.getRepeatingString(attributeName, index)
   d. Write each value to CSV with correct r_object_id
```

## Changes Made

### 1. `exportRepeatingAttribute()` Method
**Location:** Lines 344-401

**Key Changes:**
- Removed `ENABLE (ROW_BASED)` from DQL query
- Query now fetches only `r_object_id`
- Fetch each object using `session.getObject(new DfId(objectId))`
- Use `getValueCount(attributeName)` to get number of values
- Use `getRepeatingString(attributeName, index)` to get each value
- Skip null or empty values
- Better error logging with object-level granularity

**Benefits:**
- ✅ Correct r_object_id guaranteed
- ✅ Handles objects with zero values (skips them)
- ✅ Continues processing even if one object fails
- ✅ Provides detailed logging (total rows vs object count)

### 2. `exportWorkflowUsers()` Method
**Location:** Lines 407-497

**Key Changes:**
- Removed `ENABLE (ROW_BASED)` for both folder and group queries
- Fetch folder object to iterate through `cgm_and_assigned_groups`
- For each group, fetch group object to iterate through `users_names`
- Nested iteration: folders → groups → users
- Better error handling at each level

**Benefits:**
- ✅ Correct folder r_object_id for each user
- ✅ Handles missing groups gracefully
- ✅ Handles groups with no users
- ✅ More resilient to data inconsistencies

## Affected CSV Files

The following files will now have correct r_object_id values:

1. `repeating_office_type.csv`
2. `repeating_response_to_ioms_id.csv`
3. `repeating_endorse_object_id.csv`
4. `repeating_cgm_and_assigned_groups.csv`
5. `repeating_vertical_users.csv`
6. `repeating_ddm_vertical_users.csv`
7. `repeating_send_to.csv` (from movement register)
8. `repeating_workflow_users.csv`

## Performance Considerations

### Trade-offs:
- **Old approach:** Faster (single query) but unreliable
- **New approach:** Slower (fetch each object) but reliable and correct

### Performance Impact:
- For 1,000 objects with avg 3 repeating values each:
  - Old: 1 query returning 3,000 rows
  - New: 1 query + 1,000 object fetches + iteration

### Mitigation:
- The code already uses thread pools for main export
- Repeating attributes export is a one-time operation per migration
- Data integrity > speed for migration scenarios

## Testing Recommendations

1. **Verify r_object_id matching:**
   ```sql
   -- Check if exported r_object_id exists in source
   SELECT r_object_id FROM edmapp_letter_folder
   WHERE r_object_id IN (<ids from CSV>)
   ```

2. **Verify value counts:**
   - Compare total rows in CSV vs expected repeating values
   - Check for duplicates or missing values

3. **Spot check specific objects:**
   - Pick known objects with repeating attributes
   - Manually verify exported values match Documentum

4. **Cross-reference with main export:**
   - Ensure all r_object_id values in repeating CSVs exist in main metadata CSV
   - No orphaned repeating attribute rows

## Migration Impact

### Import Logic Implications:
The import process should:
1. Read main metadata CSV to create folders
2. Read repeating attribute CSVs
3. For each row, find the folder by r_object_id
4. Append the value to the repeating attribute

Example:
```java
// Import repeating attribute
String objectId = csvRow[0];  // Now guaranteed correct!
String value = csvRow[1];
IDfSysObject folder = session.getObject(new DfId(objectId));
folder.appendString(attributeName, value);
folder.save();
```

## Rollback Plan

If issues arise, the old ROW_BASED approach is preserved in git history:
```bash
git diff HEAD~1 DigidakExportOperation.java
```

## Related Files
- `DigidakExportOperation.java` - Export logic
- `DigidakImportOperation.java` - Import logic (verify compatibility)
- `application.properties` - Configuration
- `log4j2.properties` - Logging configuration
