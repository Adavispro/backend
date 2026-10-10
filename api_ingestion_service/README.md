# api_ingestion_service

Production-ready, multi-stage API Ingestion and MongoDB Loading Service for pharmaceutical manufacturing equipment (**RMG**, **FBD**, **Coating**, and **Blender**).

---

## 1. Overview & Key Capabilities

- **Zero Breaking Changes**: Completely standalone; runs alongside existing backend services without modifying existing code.
- **Robust Data Cleaning Engine**: Automatically removes HTML/XML tags (e.g. `<c>`, `<b>`), strips excess whitespace on padded keys/values, standardizes mixed datetime formats, and safely parses numeric metrics.
- **Time-Series Telemetry & Master Sync**: Ingests high-frequency metrics into MongoDB time-series collections (`iiot_ts_batch_<asset>`), records paired alarms with duration calculation, and syncs audit trails.
- **Multi-Stage Workflow Aggregation**: Automatically links stages (**Stage 1: Granulation - RMG `10094`**, **Stage 2: Drying - FBD `10110`**, **Stage 3: Blending - Blender `10012`**, **Stage 4: Coating - Coating `10021`**) into unified batch summary records in `iiot_batch_summary`.
- **Built-in Mock Service**: Simulates the Plant API (`/fwxapi/rest/v1/Dataset`) using actual real-time sample JSONs. Switching to production requires **zero code modifications**.

---

## 2. Directory Structure

```
backend/api_ingestion_service/
├── sample_data/                     # Bundled sample data for the 4 Equipments
├── config/
│   └── ingestion_config.json        # Database, API, and asset dataset mappings
├── core/
│   ├── __init__.py
│   ├── auth_manager.py              # RFC 7636 PKCE & OAuth 2.0 Token Manager
│   ├── backup_manager.py            # JSON/BSON file-based export & restore engine
│   ├── cleaner.py                   # Tag stripper, whitespace trimmer, datetime normalizer
│   ├── models.py                    # Dataclass domain models
│   ├── mongo_loader.py              # MongoDB upsert, timeseries insertion, summary aggregator
│   └── staging_reader.py            # Reader for disk-staged JSON files and manifests
├── mock_service/
│   ├── __init__.py
│   └── sample_data_server.py        # Local mock API server serving 4 Equipments sample data
├── export_database.py               # Standalone MongoDB export/backup tool
├── restore_database.py              # Standalone MongoDB restore/import tool
├── ingest_from_staging.py           # Offline / disk-based direct ingestion into MongoDB
├── ingest_scheduler.py              # Production scheduler (fetches via API -> cleans -> loads to Mongo)
├── requirements.txt                 # Module dependencies (pymongo, requests, urllib3)
└── README.md                        # Documentation
```

---

## 3. Data Cleaning Transformations

| Raw Anomaly | Raw Example | Cleaned Target | Cleaning Logic |
|---|---|---|---|
| **Padded Keys** | `"               STATUS               "` | `"STATUS"` | Keys trimmed recursively |
| **Padded Values** | `"ProductNo": "Nadolol "` | `"ProductNo": "Nadolol"` | Values trimmed |
| **Tags / Formatting** | `"<c>27"`, `"<c>MB003"`, `"<b>DRY CYCLE 1"` | `"27"`, `"MB003"`, `"DRY CYCLE 1"` | `re.sub(r'<[^>]+>', '', val).strip()` |
| **Null Placeholders** | `"CURRENT (Amp)": "-"` | `None` (omitted from numeric metrics) | Coerced to float or null |
| **Mixed Datetimes** | `"25/09/2026 14:36:10"` / `"2026-09-25T14:33:17"` | `datetime(2026, 9, 25, 14, 36, 10)` | Multi-format ISO / DD-MM-YYYY parser |
| **Recipe Sections** | `[{"Parameter": "<b>DRY CYCLE 1", "Value": ""}]` | Grouped into structured section lists | Identified and structured into sections |

---

## 4. MongoDB Schema & Collections

| Collection | Description | Key / Index |
|---|---|---|
| `products` | Product Master catalog | `product_code` (unique), `product_name` |
| `iiot_batch_summary` | Multi-stage workflow record across RMG, FBD, Blender, Coating | `batchNo` + `lotNo` + `productCode` (unique) |
| `iiot_equipment_live_status` | Real-time machine state (`Running` / `Idle`), current batch, heartbeat | `equipmentId` (unique) |
| `iiot_batch_recipes` | Recipe parameters grouped by cycle / section | `batchNo` + `lotNo` + `equipmentCode` |
| `iiot_batch_audit_trail` | Audit trail per batch and lot | `batchNo` + `lotNo` + `recordId` + `description` |
| `iiot_ts_batch_<asset>` | Time-series operational telemetry (Ampere, RPM, Temp, Damper %) | `observedAt` (timeField), `meta.batchNo`, `meta.lotNo` |
| `iiot_ts_alarm_<asset>` | Time-series alarm events (active, resolved, duration) | `event_time` (timeField), `meta.equipment_code` |
| `iiot_ts_audit_<asset>` | Time-series audit events | `event_time` (timeField), `meta.equipment_code` |
| `iiot_ingested_events_registry` | Deduplication registry with 30-day TTL | `_id`, `createdAt` (TTL index) |
| `iiot_ingestion_job_run` | Cycle job execution history | `jobRunId`, `status` |
| `iiot_ingestion_checkpoint` | Stream cursor checkpoints | `assetCode` + `streamType` |

## 5. Logs & Observability

The service uses a dual logging system:

1. **Local File & Console Logs (Disk)**:
   - File Path: `backend/api_ingestion_service/logs/ingestion.log`
   - Automatically rotated with up to 5 backups (10 MB each).
   - Captures runtime events, network requests, cleaning warnings, and cycle completion times.
2. **Database Execution & Audit Logs (MongoDB Collections)**:
   - **`iiot_ingestion_job_run`**: Records every scheduler cycle per machine, `jobRunId`, status (`RUNNING`, `SUCCESS`, `FAILED`), batch count, start/completed timestamps, and error messages.
   - **`iiot_batch_audit_trail`**: Business audit events (user ID, role, action, reason, timestamps).
   - **`iiot_ts_audit_<asset>`**: Time-series audit logs.

---

## 6. Ingestion Modes, Backup & Schedule Frequency

### Ingestion Modes:
You can configure the loader behavior via `.env` (`INGESTION_MODE`) or `config/ingestion_config.json`:
1. **`APPEND`** (Default):
   - Ingests new records incrementally.
   - Preserves all historical records in MongoDB and uses atomic deduplication to prevent duplicate entries.
2. **`TRUNCATE_AND_LOAD`**:
   - Cleans target collections before inserting fresh records.
   - **Automated Disk Backup**: When `BACKUP_BEFORE_TRUNCATE=true`, automatically exports all collections to disk as formatted, type-preserving JSON files in `backups/truncate_backup_<timestamp>/` before clearing.

### Standalone Export & Restore Tools:

#### 1. Export Database Collections to Disk
Export all collections (or specific ones) into a timestamped directory with a `manifest.json`:
```bash
# Export all collections to backups/
python backend/api_ingestion_service/export_database.py

# Export specific collections
python backend/api_ingestion_service/export_database.py --collections iiot_batch_summary products iiot_equipment_live_status
```

#### 2. Restore / Import Collections from Disk
Restore data from any exported backup folder back into MongoDB:
```bash
# Restore latest backup (replaces existing collections)
python backend/api_ingestion_service/restore_database.py

# Restore specific backup folder
python backend/api_ingestion_service/restore_database.py --backup-dir ./backups/export_20261007_234113

# Restore in append mode without dropping existing collections
python backend/api_ingestion_service/restore_database.py --append
```

### Schedule Frequency Configuration:
- **`SCHEDULE_MINUTES=15`**: Set the cycle interval in minutes (default: 15 minutes).
- **`CONTINUOUS_RUN=false`**: Toggle between single-cycle execution (`false`) and continuous scheduled background execution (`true`).

---

## 7. How to Run

### 0. Install Dependencies
```bash
pip install -r backend/api_ingestion_service/requirements.txt
```

### Mode A: Test Pipeline & Verification
Run the complete automated test suite (verifies PKCE auth, data cleaning, staging reader, mock server, and MongoDB loader):
```bash
python backend/api_ingestion_service/test_pipeline.py
```

### Mode B: Direct Ingestion from Staged Files (Offline Mode)
To ingest directly from files in the `sample_data/.../staging` directory into MongoDB:
```bash
python backend/api_ingestion_service/ingest_from_staging.py
```

### Mode C: Scheduled Ingestion via Local Mock Service (Local Realtime Testing)
1. In `config/ingestion_config.json`, ensure `"use_mock": true`.
2. Run:
```bash
python backend/api_ingestion_service/ingest_scheduler.py
```
*The mock service will automatically spin up on port 8001 and serve the 4 equipments sample JSON data.*

---

## 8. Authentication Modes & PKCE Support

The service supports multiple authentication strategies configurable via `.env` or `config/ingestion_config.json`:

1. **`AUTH_TYPE=oauth2_pkce`** (Recommended for PKCE):
   - Generates cryptographically secure `code_verifier` and `code_challenge` (S256 SHA-256 base64url, RFC 7636).
   - Requests authorization code from `auth_url` and automatically exchanges it with `code_verifier` for `access_token` and `refresh_token`.
   - Automatically handles token expiry renewal and HTTP 401 recovery.
2. **`AUTH_TYPE=oauth2_password`**: Direct resource owner password credentials grant.
3. **`AUTH_TYPE=static_token`**: Manual bearer token from `PLANT_API_BEARER_TOKEN`.
4. **`AUTH_TYPE=none`**: Local mock server testing.

### `.env` PKCE Configuration:
```ini
# Auth Mode
AUTH_TYPE=oauth2_pkce

# PKCE Endpoints & Credentials
OAUTH2_AUTH_URL=https://u3-miebmr-srv-t/fwxserverweb/security/connect/authorize
OAUTH2_TOKEN_URL=https://u3-miebmr-srv-t/fwxserverweb/security/connect/token
OAUTH2_CALLBACK_URL=http://u3-miebmr-srv-t
OAUTH2_CLIENT_ID=In_house_client
OAUTH2_USERNAME=ebr
OAUTH2_PASSWORD=1234
OAUTH2_SCOPE=openid profile fwxapi offline_access
```

---

## 9. Moving to Production (Zero Code Changes)

When deploying to production, set `USE_MOCK_API=false` in `.env` (or in `ingestion_config.json`) and run:
```bash
python backend/api_ingestion_service/ingest_scheduler.py
```

---

## 10. Security & Compliance Hardening

- **Zero Hardcoded Secrets**: All authentication keys, passwords, bearer tokens, and endpoints are loaded strictly from environment variables or secure `.env`.
- **Minimal External Dependencies**: Requires only 3 vetted libraries (`pymongo`, `requests`, `urllib3`), avoiding native C/C++ compiled binaries or OS service hooks.
- **Secure Transport & PKCE**: Implements RFC 7636 PKCE with cryptographic SHA-256 S256 verification and automatic token rotation.
- **Audit Logging**: All sync events, schema updates, and execution runs are logged to rotating files and MongoDB collection `iiot_ingestion_job_run`.



python backend/api_ingestion_service/ingest_scheduler.py --continuous --interval 15 --live


