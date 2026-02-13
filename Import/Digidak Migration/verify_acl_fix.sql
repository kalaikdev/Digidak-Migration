-- ============================================================================
-- ACL Permission Fix - Verification Queries
-- Run these queries in DQL or Documentum Administrator to verify ACL fix
-- ============================================================================

-- Query 1: Check if custom ACLs were created
-- Expected: Should return 40 ACLs (one for each folder with workflow users)
SELECT COUNT(*) as acl_count
FROM dm_acl
WHERE object_name LIKE 'acl_digidak_%';

-- Query 2: List all custom ACLs created by migration
SELECT r_object_id, object_name, owner_name, r_creation_date
FROM dm_acl
WHERE object_name LIKE 'acl_digidak_%'
ORDER BY r_creation_date DESC;

-- Query 3: Check folders using custom ACLs
-- Expected: Should return 40 folders
SELECT COUNT(*) as folder_count
FROM cms_digidak_folder
WHERE acl_name LIKE 'acl_digidak_%';

-- Query 4: List folders with their custom ACLs
SELECT r_object_id, object_name, acl_name, r_modify_date
FROM cms_digidak_folder
WHERE acl_name LIKE 'acl_digidak_%'
ORDER BY r_modify_date DESC;

-- Query 5: Verify specific test folder (4225-2024-25)
SELECT r_object_id, object_name, acl_name,
       (SELECT COUNT(*) FROM dm_acl WHERE object_name = 'acl_digidak_0b02cba082e13b29') as acl_exists
FROM cms_digidak_folder
WHERE object_name = '4225-2024-25';

-- Query 6: Check ACL permissions for test folder
-- Expected: Should show dm_owner, dm_world, eprathap, shabanbanu
SELECT accessor_name, accessor_permit,
       CASE accessor_permit
           WHEN 1 THEN 'None'
           WHEN 2 THEN 'Browse'
           WHEN 3 THEN 'Read'
           WHEN 4 THEN 'Relate'
           WHEN 5 THEN 'Version'
           WHEN 6 THEN 'Write'
           WHEN 7 THEN 'Delete'
           ELSE 'Unknown'
       END as permit_name
FROM dm_acl
WHERE object_name = 'acl_digidak_0b02cba082e13b29'
ORDER BY accessor_name;

-- Query 7: Verify user resolution
-- Expected: Should return login names for workflow users
SELECT user_name, user_login_name, user_state
FROM dm_user
WHERE user_name IN ('E Prathap', 'Smt Shaban Banu')
ORDER BY user_name;

-- Query 8: Count workflow users in each ACL
SELECT a.object_name as acl_name,
       (SELECT COUNT(*) FROM dm_acl WHERE r_object_id = a.r_object_id) as total_accessors,
       (SELECT object_name FROM cms_digidak_folder WHERE acl_name = a.object_name) as folder_name
FROM dm_acl a
WHERE a.object_name LIKE 'acl_digidak_%'
ORDER BY a.r_creation_date DESC;

-- Query 9: Check for folders WITHOUT custom ACLs (should use parent ACL)
-- These are folders that had no workflow users
SELECT r_object_id, object_name, acl_name
FROM cms_digidak_folder
WHERE acl_name NOT LIKE 'acl_digidak_%'
ORDER BY object_name;

-- Query 10: Detailed permissions report for all workflow users
-- Shows which users have access to which folders
SELECT f.object_name as folder_name,
       f.r_object_id as folder_id,
       a.object_name as acl_name,
       (SELECT accessor_name FROM dm_acl WHERE object_name = a.object_name AND accessor_name NOT LIKE 'dm_%' AND accessor_name NOT LIKE '%admin%') as workflow_users
FROM cms_digidak_folder f, dm_acl a
WHERE f.acl_name = a.object_name
  AND f.acl_name LIKE 'acl_digidak_%'
ORDER BY f.object_name;

-- ============================================================================
-- EXPECTED RESULTS FOR TEST FOLDER (4225-2024-25)
-- ============================================================================
-- Query 5: Should return 1 row with acl_name = 'acl_digidak_0b02cba082e13b29'
-- Query 6: Should return 4 rows:
--   - dm_owner: 7 (Delete)
--   - dm_world: 2 (Browse)
--   - eprathap: 3 (Read)
--   - shabanbanu: 3 (Read)
-- Query 7: Should return 2 rows:
--   - E Prathap -> eprathap
--   - Smt Shaban Banu -> shabanbanu
-- ============================================================================

-- Query 11: Check if ACL fix is working - Compare workflow_groups with ACL
-- This query shows folders where workflow_groups attribute is set
SELECT f.r_object_id, f.object_name, f.acl_name,
       (SELECT COUNT(*) FROM dm_acl WHERE object_name = f.acl_name) as acl_accessor_count
FROM cms_digidak_folder f
WHERE f.r_object_id IN (
    SELECT DISTINCT r_object_id
    FROM cms_digidak_folder
    WHERE workflow_groups IS NOT NULL
)
ORDER BY f.object_name;

-- ============================================================================
-- TROUBLESHOOTING QUERIES
-- ============================================================================

-- T1: Find orphaned ACLs (ACLs created but not used by any folder)
SELECT a.r_object_id, a.object_name
FROM dm_acl a
WHERE a.object_name LIKE 'acl_digidak_%'
  AND NOT EXISTS (
      SELECT 1 FROM cms_digidak_folder f WHERE f.acl_name = a.object_name
  );

-- T2: Find folders with workflow_groups but no custom ACL
SELECT r_object_id, object_name, acl_name
FROM cms_digidak_folder
WHERE workflow_groups IS NOT NULL
  AND acl_name NOT LIKE 'acl_digidak_%';

-- T3: List all workflow_groups values
SELECT r_object_id, object_name, workflow_groups
FROM cms_digidak_folder
WHERE workflow_groups IS NOT NULL
ORDER BY object_name;

-- ============================================================================
-- End of verification queries
-- ============================================================================
