# Digidak Migration - Import Task

## Objective
Import Digidak folder records from CSV to Documentum repository.

## Steps
- [x] Configure `DigidakImportOperation` to import only `cms_digidak_folder` (skipped children)
- [x] Update `run-import.bat` with Java 9+ compatibility flags (`--add-opens`)
- [x] Add compiled classes to `run-import.bat` classpath
- [x] Execute import successfully (Bypassed TBO issue by using dm_folder)
- [x] Verify imported records (2 records imported as dm_folder)
