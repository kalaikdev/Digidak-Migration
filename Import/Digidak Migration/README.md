# DigiDak Migration - Documentum DFC Bulk Import Application

## Overview

DigiDak Migration is a production-ready Java application for bulk importing DigiDak contents into a Documentum repository using Documentum Foundation Classes (DFC). The application supports concurrent processing, proper metadata handling, ACL management, and movement register creation.

## Features

- ✅ **Bulk Document Import**: Import thousands of documents efficiently
- ✅ **Concurrent Processing**: Multi-threaded import for optimal performance
- ✅ **Metadata Management**: Automatic metadata extraction and assignment from CSV files
- ✅ **Folder Structure Management**: Automated creation of cabinet and folder hierarchy
- ✅ **ACL Inheritance**: Apply parent folder ACLs to documents automatically
- ✅ **Repeating Attributes**: Proper handling of Documentum repeating attributes
- ✅ **Movement Registers**: Automatic creation of movement registers for tracking
- ✅ **Error Handling**: Comprehensive error handling with detailed logging
- ✅ **Production Ready**: Connection pooling, retry logic, and performance optimizations

## Architecture

### Project Structure

```
digidak-migration/
├── src/
│   ├── main/
│   │   ├── java/com/digidak/migration/
│   │   │   ├── DigidakMigrationApp.java      # Main application
│   │   │   ├── config/                        # Configuration classes
│   │   │   │   ├── DfcConfig.java
│   │   │   │   └── MigrationConfig.java
│   │   │   ├── model/                         # Domain models
│   │   │   │   ├── DocumentMetadata.java
│   │   │   │   ├── FolderInfo.java
│   │   │   │   ├── SchemaAttribute.java
│   │   │   │   └── ImportResult.java
│   │   │   ├── repository/                    # Data access layer
│   │   │   │   ├── SessionManager.java
│   │   │   │   ├── FolderRepository.java
│   │   │   │   └── DocumentRepository.java
│   │   │   ├── service/                       # Business logic
│   │   │   │   ├── FolderService.java
│   │   │   │   ├── DocumentImportService.java
│   │   │   │   ├── AclService.java
│   │   │   │   └── MovementRegisterService.java
│   │   │   ├── parser/                        # CSV and schema parsers
│   │   │   │   ├── SchemaParser.java
│   │   │   │   └── MetadataCsvParser.java
│   │   │   ├── processor/                     # Concurrent processing
│   │   │   │   └── ConcurrentImportProcessor.java
│   │   │   └── util/                          # Utilities
│   │   │       ├── DateUtil.java
│   │   │       └── RepeatingAttributeHandler.java
│   │   └── resources/
│   │       └── log4j2.xml                     # Logging configuration
│   └── test/                                   # Unit tests
├── config/                                     # Configuration files
│   ├── dfc.properties
│   ├── migration.properties
│   └── log4j2.properties
├── DigidakMetadata_Export/                    # Sample data
├── DocumentumSchema/                          # Schema definitions
├── libs/                                       # DFC JAR files
├── pom.xml                                     # Maven configuration
└── README.md
```

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Documentum Repository access
- DFC JARs (provided in `libs/` folder)

## Configuration

### 1. DFC Configuration (`config/dfc.properties`)

```properties
dfc.docbroker.host=your-docbroker-host
dfc.docbroker.port=1489
dfc.repository.name=your-repository-name
dfc.username=your-username
dfc.password=your-password
dfc.session.pool.size=10
```

### 2. Migration Configuration (`config/migration.properties`)

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

## Building the Application

```bash
# Compile and package
mvn clean package

# Run tests
mvn test

# Create executable JAR with dependencies
mvn clean package assembly:single
```

## Running the Application

```bash
# Run with Maven
mvn exec:java -Dexec.mainClass="com.digidak.migration.DigidakMigrationApp"

# Run JAR directly
java -jar target/digidak-migration-1.0.0-jar-with-dependencies.jar
```

## Import Process

The application follows a systematic 3-phase approach:

### Phase 1: Folder Structure Setup
1. Create `/Digidak Legacy` cabinet
2. Create single record folders (e.g., `4224-2024-25`, `4225-2024-25`)
3. Create group record folders (e.g., `G65-2024-25`)
4. Create subletter folders under respective group folders

### Phase 2: Document Import
1. Parse metadata from CSV files
2. Import documents to single record and group record folders
3. Skip document import for subletter folders (metadata only)
4. Apply metadata and ACLs
5. Attach content files

### Phase 3: Movement Register Creation
1. Create movement registers for single record folders
2. Create movement registers for subletter folders
3. Link registers to their respective folder IDs

## Performance Features

### Concurrency
- Configurable thread pool (default: CPU cores × 2)
- Parallel folder processing
- Thread-safe session management

### Connection Pooling
- DFC session pooling (default: 10 sessions)
- Automatic session acquisition and release
- Connection health checks

### Optimization
- Batch operations where possible
- Folder and ACL caching
- Streaming CSV parsing (memory efficient)

### Error Handling
- Retry logic with exponential backoff
- Detailed error logging
- Graceful degradation
- Progress tracking

## Logging

Logs are written to:
- `logs/digidak-migration.log` - All logs
- `logs/digidak-migration-error.log` - Error logs only
- Console output with real-time progress

## Reports

After completion, the application generates:
- HTML report with detailed statistics
- Error summary (if any)
- Performance metrics

## Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=MetadataCsvParserTest

# Run with coverage
mvn clean test jacoco:report
```

## Troubleshooting

### Common Issues

1. **Connection Timeout**
   - Check DFC configuration
   - Verify docbroker accessibility
   - Increase session pool size

2. **OutOfMemoryError**
   - Reduce thread pool size
   - Decrease batch size
   - Increase JVM heap: `-Xmx4g`

3. **Metadata Parsing Errors**
   - Verify CSV format
   - Check date format in metadata
   - Ensure UTF-8 encoding

4. **Permission Issues**
   - Verify repository credentials
   - Check folder permissions
   - Validate ACL configuration

## Support

For issues or questions, please refer to:
- Project documentation
- Documentum DFC API documentation
- Application logs for detailed error messages

## License

Proprietary - Internal Use Only

## Version History

- **1.0.0** (2024) - Initial release with full feature set
