# DigiDak Migration - Deployment Guide

## Prerequisites

Before deploying the DigiDak Migration application, ensure you have:

### Software Requirements
- ✅ **Java Runtime Environment (JRE) 11 or higher**
- ✅ **Access to Documentum Repository**
- ✅ **Documentum DFC installed and configured**
- ✅ **Network connectivity to Docbroker**

### Permissions Required
- ✅ **Repository credentials** with import permissions
- ✅ **Cabinet creation permissions**
- ✅ **Folder creation permissions**
- ✅ **Document import permissions**
- ✅ **ACL management permissions**

---

## Step 1: Build the Application

### Option A: Build with Maven (Recommended)

```bash
# Navigate to project directory
cd "c:\Workspace\Digidak Migration"

# Clean and build
mvn clean package

# Build with dependencies (creates fat JAR)
mvn clean package assembly:single
```

**Output:** `target/digidak-migration-1.0.0-jar-with-dependencies.jar`

### Option B: Use Pre-built JAR

If you have a pre-built JAR, skip to Step 2.

---

## Step 2: Configuration

### 2.1 Configure DFC Connection

Edit `config/dfc.properties`:

```properties
# Docbroker Configuration
dfc.docbroker.host=your-docbroker-hostname
dfc.docbroker.port=1489

# Repository Configuration
dfc.repository.name=YourRepositoryName
dfc.username=your-username
dfc.password=your-password

# Session Pool
dfc.session.pool.size=10
```

**Security Note:** For production, consider encrypting the password or using credential management systems.

### 2.2 Configure Migration Settings

Edit `config/migration.properties`:

```properties
# Thread pool configuration
migration.threadpool.size=8
migration.batch.size=10

# Retry configuration
migration.retry.attempts=3
migration.retry.delay.ms=1000

# Data paths
migration.data.export.path=DigidakMetadata_Export
migration.schema.path=DocumentumSchema/object model.csv

# Cabinet configuration
migration.cabinet.name=Digidak Legacy
```

**Performance Tuning:**
- **Small datasets (<1000 docs):** `threadpool.size=4`
- **Medium datasets (1000-10000 docs):** `threadpool.size=8`
- **Large datasets (>10000 docs):** `threadpool.size=16`

---

## Step 3: Prepare Data

### 3.1 Verify Directory Structure

Ensure your data is organized as follows:

```
DigidakMetadata_Export/
├── digidak_single_records/
│   ├── 4224-2024-25/
│   │   ├── document_metadata.csv
│   │   └── Keonjhar CCB.pdf
│   └── 4225-2024-25/
│       ├── document_metadata.csv
│       └── document.pdf
├── digidak_group_records/
│   └── G65-2024-25/
│       ├── document_metadata.csv
│       └── 18.pdf
└── digidak_subletter_records/
    ├── 4245-2024-25/
    │   └── document_metadata.csv
    └── 4246-2024-25/
        └── document_metadata.csv
```

### 3.2 Validate Metadata CSV Files

Each `document_metadata.csv` should have:

```csv
r_object_id,object_name,r_object_type,i_folder_id,r_folder_path,r_creator_name,r_creation_date,document_type
0902cba082e13b0c,Keonjhar CCB.pdf,edmapp_letter_document,0b02cba082e13b24,/Letter/4224-2024-25,Binodini Behera,"01/05/2024, 11:58:47 AM",Main Letter
```

**Required Fields:**
- `object_name` - Document filename
- `r_object_type` - Documentum object type
- `r_creator_name` - Creator username
- `r_creation_date` - Creation timestamp
- `document_type` - Document type

---

## Step 4: Pre-Deployment Validation

### 4.1 Test Connection

Create a simple test script `test-connection.sh`:

```bash
#!/bin/bash
java -cp "libs/*:target/digidak-migration-1.0.0-jar-with-dependencies.jar" \
  com.digidak.migration.config.DfcConfig
```

Run and verify no connection errors.

### 4.2 Dry Run (Optional)

For safety, you can perform a dry run:

1. Create a test cabinet in repository
2. Update `migration.cabinet.name` to test cabinet
3. Run migration with small dataset
4. Verify results
5. Delete test cabinet

---

## Step 5: Deploy and Execute

### 5.1 Deploy Application Files

Copy the following to your deployment server:

```
deployment/
├── digidak-migration-1.0.0-jar-with-dependencies.jar
├── config/
│   ├── dfc.properties
│   ├── migration.properties
│   └── log4j2.xml
├── DigidakMetadata_Export/
├── DocumentumSchema/
├── libs/
└── logs/  (create this directory)
```

### 5.2 Set Environment Variables (Optional)

```bash
export JAVA_HOME=/path/to/java11
export DFC_DATA_DIR=/path/to/dfc/config
export LOG_DIR=/path/to/logs
```

### 5.3 Execute Migration

#### Windows:

```batch
@echo off
java -Xmx4g -Xms1g ^
  -Dlog4j.configurationFile=config/log4j2.xml ^
  -jar target/digidak-migration-1.0.0-jar-with-dependencies.jar
```

#### Linux/Unix:

```bash
#!/bin/bash
java -Xmx4g -Xms1g \
  -Dlog4j.configurationFile=config/log4j2.xml \
  -jar target/digidak-migration-1.0.0-jar-with-dependencies.jar
```

**Memory Settings:**
- `-Xmx4g` - Maximum heap size (adjust based on dataset size)
- `-Xms1g` - Initial heap size

---

## Step 6: Monitor Execution

### 6.1 Monitor Console Output

The application will display real-time progress:

```
2026-02-11 10:00:00 INFO  - DigiDak Migration Application Starting
2026-02-11 10:00:01 INFO  - Phase 1: Setting up folder structure
2026-02-11 10:00:05 INFO  - Cabinet created: Digidak Legacy
2026-02-11 10:00:06 INFO  - Phase 2: Importing documents concurrently
2026-02-11 10:05:30 INFO  - Imported 1000 documents
2026-02-11 10:10:00 INFO  - Phase 3: Creating movement registers
2026-02-11 10:12:00 INFO  - Migration Completed Successfully
```

### 6.2 Monitor Log Files

```bash
# Real-time monitoring
tail -f logs/digidak-migration.log

# Monitor errors only
tail -f logs/digidak-migration-error.log

# Check for specific patterns
grep "ERROR" logs/digidak-migration.log
grep "Successfully imported" logs/digidak-migration.log | wc -l
```

---

## Step 7: Post-Execution Validation

### 7.1 Review Import Results

Check the HTML report generated:

```
migration_report_20260211_100000.html
```

**Key Metrics to Verify:**
- Total Documents Processed
- Successful Imports
- Failed Imports (should be 0 or minimal)
- Folders Created
- Movement Registers Created
- Duration

### 7.2 Verify in Repository

Using Documentum Administrator or Webtop:

1. **Verify Cabinet:**
   - Navigate to `/Digidak Legacy`
   - Confirm cabinet exists

2. **Verify Folders:**
   - Check single record folders (e.g., `4224-2024-25`)
   - Check group record folders (e.g., `G65-2024-25`)
   - Check subletter folders under groups

3. **Verify Documents:**
   - Open sample documents
   - Check metadata fields
   - Verify content is attached
   - Confirm ACLs are applied

4. **Verify Movement Registers:**
   - Check for movement register documents
   - Verify they are linked to correct folders

### 7.3 Verify Counts

Run DQL queries to verify counts:

```sql
-- Count documents by type
SELECT count(*)
FROM cms_digidak_document
WHERE FOLDER('/Digidak Legacy', DESCEND);

-- Count folders
SELECT count(*)
FROM cms_digidak_folder
WHERE FOLDER('/Digidak Legacy', DESCEND);

-- Count movement registers
SELECT count(*)
FROM cms_movement_register
WHERE FOLDER('/Digidak Legacy', DESCEND);
```

---

## Step 8: Troubleshooting

### Common Issues

#### Issue 1: Connection Timeout

**Error:** `Timeout waiting for available session`

**Solution:**
- Increase `dfc.session.pool.size` in `dfc.properties`
- Reduce `migration.threadpool.size` in `migration.properties`
- Check network connectivity to docbroker

#### Issue 2: OutOfMemoryError

**Error:** `java.lang.OutOfMemoryError: Java heap space`

**Solution:**
- Increase JVM heap: `-Xmx8g` (or higher)
- Reduce `migration.threadpool.size`
- Reduce `migration.batch.size`

#### Issue 3: Metadata Parsing Errors

**Error:** `Error parsing row X in file...`

**Solution:**
- Verify CSV format (comma-separated)
- Check for special characters in metadata
- Ensure UTF-8 encoding
- Validate date formats

#### Issue 4: Permission Denied

**Error:** `Permission denied` or `Access violation`

**Solution:**
- Verify user has import permissions
- Check ACL on target cabinet
- Ensure user can create folders
- Verify document type permissions

---

## Step 9: Rollback (If Needed)

If migration fails or needs to be rolled back:

### Option 1: Delete Cabinet

```sql
-- Delete all content
DELETE FROM dm_sysobject
WHERE FOLDER('/Digidak Legacy', DESCEND);

-- Delete folders
DELETE FROM dm_folder
WHERE FOLDER('/Digidak Legacy', DESCEND);

-- Delete cabinet
DELETE FROM dm_cabinet
WHERE object_name = 'Digidak Legacy';
```

### Option 2: Destroy Cabinet (More thorough)

Using DQL or API:
```java
// Destroy cabinet and all content
IDfSession session = getSession();
IDfFolder cabinet = (IDfFolder) session.getObjectByPath("/Digidak Legacy");
cabinet.destroyAllVersions();
```

---

## Step 10: Production Checklist

Before going to production:

- [ ] Configuration files reviewed and validated
- [ ] DFC connection tested successfully
- [ ] Test run completed with sample data
- [ ] Backup of existing repository data (if applicable)
- [ ] Monitoring and alerting configured
- [ ] Log rotation configured
- [ ] Sufficient disk space for logs
- [ ] JVM memory settings tuned for dataset size
- [ ] Thread pool size optimized
- [ ] Rollback plan documented and tested
- [ ] Operations team trained
- [ ] Support contacts identified

---

## Performance Tuning Guide

### For Small Datasets (<1,000 documents)

```properties
migration.threadpool.size=4
migration.batch.size=10
dfc.session.pool.size=5
```

```bash
java -Xmx2g -Xms512m ...
```

### For Medium Datasets (1,000 - 10,000 documents)

```properties
migration.threadpool.size=8
migration.batch.size=10
dfc.session.pool.size=10
```

```bash
java -Xmx4g -Xms1g ...
```

### For Large Datasets (>10,000 documents)

```properties
migration.threadpool.size=16
migration.batch.size=20
dfc.session.pool.size=20
```

```bash
java -Xmx8g -Xms2g ...
```

---

## Support and Maintenance

### Log Files Location

- **All Logs:** `logs/digidak-migration.log`
- **Error Logs:** `logs/digidak-migration-error.log`
- **HTML Reports:** `migration_report_*.html`

### Regular Maintenance

- Monitor log file sizes (configure rotation)
- Archive old HTML reports
- Clean up temp files periodically
- Review and update configurations as needed

---

## Contact Information

For issues or questions:
- Check application logs
- Review this deployment guide
- Refer to README.md for detailed documentation
- Contact: [Your support team contact]

---

**Deployment Status:** Ready for Production ✅

**Last Updated:** February 11, 2026
