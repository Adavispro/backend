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

### 1. Ingest a Specific Date (e.g. 2026-09-25)
```bash
python ingest_date_run.py --date 2026-09-25
```

### 2. Ingest All Discovered Dates (e.g. Month of September 2026)
```bash
python ingest_date_run.py --all-dates
```

### 3. Run the Background Scheduler Service
```bash
# Run continuous daemon (default every 15 minutes)
python ingest_file_scheduler.py

# Run a single scheduled pass
python ingest_file_scheduler.py --once
```

---

## 6. Verification Status

- **Batch Reports Ingested**: 87 Production Reports
- **Alarms Extracted & Staged**: 935 Events
- **Audits & Parameter Changes**: 2,723 Events
- **Generated PDF Reports**: 87 (Saved to `staging/generated_pdfs/<date>/`)
- **Extracted JSON Datasets**: 111 (Saved to `staging/extracted_json/<date>/`)
- **Stage 4 Synchronization**: Verified with `equipmentId: "MC081"`, `equipmentType: "COMP"`, `operatorName`, and good counters.
