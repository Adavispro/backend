# Sejong Compression File Ingestion Service (MC081 - Stage 4)

Production-grade file ingestion and data synchronization service for **Sejong 49D Tablet Press (`MC081`)** in the ADAVIS IIoT Platform.

---

## 1. Overview & Architecture

The **File Ingestion Service** mirrors the reliability and performance of the API ingestion service while handling date-wise file synchronization from remote/local machine storage paths.

### High-Level Workflow:
```
[Remote / Local Backup Source]
       │ (Date-wise scanning: 2026-09-01 to 2026-09-30)
       ▼
[FileFetcher Staging Engine] ────► staging/raw/<YYYY-MM-DD>/
       │
       ├─► [ProductionReport Excel Parser] ──► Parse Batch Info, Settings, Pressures, Counters, Signatures
       │         │
       │         ├─► staging/extracted_json/<YYYY-MM-DD>/<Report>.json
       │         └─► staging/generated_pdfs/<YYYY-MM-DD>/<Report>.pdf (ReportLab 2-page PDF)
       │
       └─► [Sawc MDB Reader] ──► SawcData.mdb (SAWC_DATA, AWC Telemetry) + Sawc.mdb (Lookups)
                 │
                 └─► staging/extracted_json/<YYYY-MM-DD>/SawcEvents-<Date>.json
                           │
                           ▼
                 [MongoCompressionLoader]
                 ├─► iiot_ts_batch_MC081 (87 Batch Runs)
                 ├─► iiot_ts_alarm_MC081 (935 Alarms)
                 ├─► iiot_ts_audit_MC081 (2,723 Audit & Operations)
                 ├─► iiot_batch_recipes (29 Recipe Profiles)
                 ├─► iiot_batch_summary (Stage 4 Compression Sync)
                 └─► iiot_equipment_live_status (MC081 Live Telemetry)
```

---

## 2. Equipment Specifications

- **Equipment ID / Code**: `MC081`
- **Equipment Name**: `MC081 SEJONG 49D Compression Machine`
- **Model**: `Sejong 49D`
- **Equipment Type**: `COMP`
- **Workflow Stage**: `STAGE-4` (`Compression`)
- **Stage Order**: `4`

---

## 3. Database Schema Alignment

| MongoDB Collection | Content | Key Fields / Indexes |
| :--- | :--- | :--- |
| `iiot_ts_batch_MC081` | Time-series production batch reports | `batchId`, `equipmentId: "MC081"`, `pressureData`, `operationValues`, `tabletCounters`, `operatorName`, `timestamp` |
| `iiot_ts_alarm_MC081` | Machine alarms & safety messages from `SAWC_DATA` | `alarmId`, `alarmCode`, `alarmName`, `severity`, `timestamp`, `productName`, `batchNumber` |
| `iiot_ts_audit_MC081` | Parameter changes & login/logout audits | `auditId`, `eventType`, `action`, `previousValue`, `newValue`, `userId`, `operatorName`, `timestamp` |
| `iiot_batch_summary` | Overall batch workflow summary | Updates `stages[STAGE-4]` with `equipmentCode: "MC081"`, `equipmentType: "COMP"`, `operatorName`, `producedQuantity`, `criticalMetrics` |
| `iiot_equipment_live_status` | Current machine status | `equipmentCode: "MC081"`, `status: "RUNNING"`, `currentBatch`, `liveData` (disk speed, pressures, tablet counters) |
| `iiot_batch_recipes` | Recipe setpoints & control limits | `equipmentCode: "MC081"`, `batchNo`, `productName`, `parameters` (limits, cams, lubrication, feeder) |

---

## 4. Directory Structure

```
backend/compression_server/
├── config/
│   └── compression_config.json      # Ingestion, database, and staging configuration
├── core/
│   ├── cleaner.py                   # Data cleaning, tag stripping, datetime parsing
│   ├── file_fetcher.py              # Incremental file fetcher & sha256 staging engine
│   ├── production_report_parser.py  # ProductionReport-*.xls parser
│   ├── sawc_mdb_reader.py           # SawcData.mdb & Sawc.mdb OLEDB reader
│   ├── pdf_generator.py             # ReportLab 2-page Sejong PDF generator
│   └── mongo_loader.py              # MongoDB batch, alarm, audit & live sync loader
├── staging/
│   ├── raw/                         # Staged raw Excel and MDB files
│   ├── extracted_json/              # Structured JSON data exports (date-wise)
│   └── generated_pdfs/              # Generated 2-page PDF reports (date-wise)
├── logs/                            # Service execution and scheduler logs
├── ingest_date_run.py               # CLI date runner (single date / all dates)
├── ingest_file_scheduler.py         # Scheduled daemon service
├── requirements.txt                 # Python dependencies
└── .env                             # Environment configuration
```

---

## 5. Usage & Execution

Run the commands below from `backend\file_ingestion_service` in PowerShell,
using the same Python environment that passed the MDB connection test.
From the workspace root:

```powershell
Set-Location .\backend\file_ingestion_service
```

### Windows MDB provider

Direct MDB reading requires `pywin32` in the ingestion Python environment and
an installed ACE OLE DB provider matching Python's 32/64-bit architecture.
Full Microsoft Office is not required.

The default provider remains `Microsoft.ACE.OLEDB.16.0`. For a server with
ACE 12.0 installed, select it in the same PowerShell session before running
either ingestion entry point:

```powershell
$env:MDB_OLEDB_PROVIDER = "Microsoft.ACE.OLEDB.12.0"
python ingest_date_run.py --all-dates
```

Only `Microsoft.ACE.OLEDB.16.0` and `Microsoft.ACE.OLEDB.12.0` are accepted.
The date runner also loads this setting from its `.env` file; an existing
process environment setting takes precedence. Provider selection does not
install a driver or change Microsoft's OS/runtime support limitations.
Verify an actual MDB connection before ingestion. When COM and the MDB file
are available, ingestion reads the MDB directly, bypassing cached history JSON.
Connection or event-query failures stop ingestion before exporting that date's
histories. Staged JSON remains a fallback when COM or the MDB is unavailable;
without either source, ingestion raises an error. A missing daily table is
logged as a warning and returns no events for that date.
Look for `Reading SAWC_DATA_...` and `Extracted MDB events...` in the console.
Do not truncate the database to troubleshoot provider errors. Re-ingestion
updates existing data and can reset MC081 stage approvals and live status;
back up production data and review those effects before replaying reports.

### 1. Ingest a Specific Date (e.g. 2026-09-25)
```powershell
$env:INGESTION_MODE = "APPEND"
python ingest_date_run.py --date 2026-09-25
```

### Repair missing alarm, operating and login histories only

Use this instead of truncation when production reports are already loaded.
Deploy the updated date runner, MDB reader and Mongo loader together, stop
concurrent ingestion, and back up the target database before running:

```powershell
$env:MDB_OLEDB_PROVIDER = "Microsoft.ACE.OLEDB.16.0"
$env:INGESTION_MODE = "APPEND"
python ingest_date_run.py --date 2026-10-05 --events-only
if ($LASTEXITCODE -ne 0) { throw "MC081 history repair failed; review the error before continuing" }
```

After checking that date, repair every discovered source date:

```powershell
$env:MDB_OLEDB_PROVIDER = "Microsoft.ACE.OLEDB.16.0"
$env:INGESTION_MODE = "APPEND"
python ingest_date_run.py --all-dates --events-only
if ($LASTEXITCODE -ne 0) { throw "MC081 history repair failed; review the error before continuing" }
```

The repair upserts MC081 event IDs into `iiot_ts_alarm_MC081`,
`iiot_ts_audit_MC081`, `iiot_ts_login_MC081` and their shared mirrors
`iiot_alarm_events`, `iiot_batch_audit_trail` (operations with a batch identity),
and `login_history`. Each destination is verified by event ID and equipment
after writing. Existing event IDs are updated rather than deleted.
Missing event IDs are inserted, so events do not need to exist in MongoDB first.
With direct MDB access available, each selected date is extracted again from
the MDB rather than from cached JSON. `--all-dates` selects discovered source
date folders, not every table in the MDB.
It also refreshes only the three embedded history arrays in existing
`iiot_ts_batch_MC081` reports whose stored source filename matches a production
report in that date folder. It does not parse/reload production reports, change
recipes, batch summaries, approvals, live status, masters or checkpoints, or
delete any documents. The three date-level history JSON files are regenerated.
Database event writes and verification happen before exporting
`AlarmHistory-<date>.json`, `OperatingHistory-<date>.json` and
`LogInOutHistory-<date>.json`; existing files are overwritten after those steps
succeed. Missing production reports are not created by this mode.
Check the logged embedded-report match count; legacy reports without the
current source-file field require separate migration.

An explicit `--date` or `--all-dates` is required; checkpoint resume is not used.
Truncation and `--limit` are rejected. Dates with a missing MDB event table or
no extracted events are logged and skipped for database repair; existing
database records and embedded histories for those dates remain untouched.
The three JSON files are still exported with empty lists, and `--all-dates`
continues to the next date. If only some categories have events, those events
are upserted and all three embedded arrays reflect that date's extraction.
Connection, query, database mirror
write and verification failures stop the repair with a nonzero exit.
Writes across collections/dates are not atomic; earlier successful writes may
remain after a failure. Correct the error and rerun the same command.

Confirm these console messages before considering the repair complete:

```text
Reading SAWC_DATA_... ... (bypassing cached history JSON)
Extracted MDB events for ...: alarms=..., operations=..., logins=...
Verified iiot_ts_alarm_MC081: ... MC081 event identities
Verified iiot_alarm_events: ... MC081 event identities
Verified iiot_ts_audit_MC081: ... MC081 event identities
Verified iiot_batch_audit_trail: ... MC081 event identities
Verified iiot_ts_login_MC081: ... MC081 event identities
Verified login_history: ... MC081 event identities
Refreshed embedded histories in ... existing MC081 reports
Events-only repair completed for ...; production data/checkpoint unchanged
```

### 2. Ingest All Discovered Dates (e.g. Month of September 2026)
```powershell
$env:INGESTION_MODE = "APPEND"
python ingest_date_run.py --all-dates
```

### 3. Run the Background Scheduler Service
```powershell
# Run continuous daemon (default every 15 minutes)
python .\ingest_file_scheduler.py

# Run a single scheduled pass
python .\ingest_file_scheduler.py --once
```

---

## 6. Verification Status

- **Batch Reports Ingested**: 87 Production Reports
- **Alarms Extracted & Staged**: 935 Events
- **Audits & Parameter Changes**: 2,723 Events
- **Generated PDF Reports**: 87 (Saved to `staging/generated_pdfs/<date>/`)
- **Extracted JSON Datasets**: 111 (Saved to `staging/extracted_json/<date>/`)
- **Stage 4 Synchronization**: Verified with `equipmentId: "MC081"`, `equipmentType: "COMP"`, `operatorName`, and good counters.
