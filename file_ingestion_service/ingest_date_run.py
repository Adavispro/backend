"""
File Ingestion CLI Engine for Sejong Compression Machine (MC081).
Executes date-wise ingestion for specified dates or all discovered backup dates.
Focuses strictly on: Copy -> Extract -> Transform to JSON -> Load to DB.
"""

import os
import sys
import json
import glob
import logging
import argparse
from datetime import datetime, timezone
from typing import List, Dict, Any, Optional

from pathlib import Path

# Ensure file_ingestion_service directory is in sys.path
file_svc_dir = Path(__file__).resolve().parent
if str(file_svc_dir) not in sys.path:
    sys.path.insert(0, str(file_svc_dir))

try:
    from core.file_fetcher import FileFetcher
    from core.production_report_parser import parse_production_report_xls
    from core.sawc_mdb_reader import SawcMdbReader
    from core.mongo_loader import MongoCompressionLoader
except ImportError:
    from file_ingestion_service.core.file_fetcher import FileFetcher
    from file_ingestion_service.core.production_report_parser import parse_production_report_xls
    from file_ingestion_service.core.sawc_mdb_reader import SawcMdbReader
    from file_ingestion_service.core.mongo_loader import MongoCompressionLoader

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("compression.cli")

def load_dotenv_if_present() -> None:
    """Zero-dependency .env loader for file_ingestion_service."""
    for env_path in [file_svc_dir / ".env", file_svc_dir.parent / ".env", Path(".env")]:
        if env_path.exists():
            try:
                with open(env_path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line and not line.startswith("#") and "=" in line:
                            k, v = line.split("=", 1)
                            k, v = k.strip(), v.strip().strip("'\"")
                            if k and k not in os.environ:
                                os.environ[k] = v
            except Exception:
                pass

load_dotenv_if_present()


def load_config(config_path: Optional[str] = None) -> Dict[str, Any]:
    """Load compression server configuration with multi-path resolution."""
    if config_path:
        candidates = [
            Path(config_path),
            file_svc_dir / config_path,
            file_svc_dir / "config" / config_path,
        ]
    else:
        candidates = [
            file_svc_dir / "config" / "file_ingestion_config.json",
            Path("config/file_ingestion_config.json"),
            Path("backend/file_ingestion_service/config/file_ingestion_config.json"),
        ]
    for c in candidates:
        if c.exists():
            with open(c, "r", encoding="utf-8") as f:
                return json.load(f)
    raise FileNotFoundError(f"Configuration file not found in candidates: {candidates}")


def ingest_single_date(
    target_date: str,
    config: Dict[str, Any],
    file_fetcher: FileFetcher,
    mdb_reader: SawcMdbReader,
    mongo_loader: MongoCompressionLoader,
    batch_limit: Optional[int] = None
) -> Dict[str, Any]:
    """
    Ingests production reports and SawcData events for a single date (YYYY-MM-DD).
    Respects batch_limit if provided.
    """
    logger.info(f"============================================================")
    logger.info(f"Starting Ingestion for Date: {target_date} (Equipment: MC081)")
    logger.info(f"============================================================")

    # 1. Fetch / Sync files for the date into staging
    copied_files = file_fetcher.fetch_date_files(target_date)
    date_staging_dir = os.path.join(file_fetcher.staged_raw_dir, target_date)
    
    # Check source directly if staging is same as source or check both
    xls_files = glob.glob(os.path.join(date_staging_dir, "ProductionReport-*.xls"))
    if not xls_files:
        src_path = config.get("source", {}).get("local_backup_path", "../compression_server")
        if not os.path.exists(src_path):
            src_path = str((Path(__file__).resolve().parent.parent / "compression_server").resolve())
        xls_files = glob.glob(os.path.join(src_path, target_date, "ProductionReport-*.xls"))

    xls_files = sorted(xls_files)
    logger.info(f"Found {len(xls_files)} ProductionReport Excel files for {target_date}")

    ext_dir = config.get("output_paths", {}).get("extracted_json_dir", "./staging/extracted_json")
    if not os.path.isabs(ext_dir):
        ext_dir = str((Path(__file__).resolve().parent / ext_dir).resolve())
    out_json_dir = os.path.join(ext_dir, target_date)
    os.makedirs(out_json_dir, exist_ok=True)

    # 2. Read SawcData.mdb alarms, operations, and login history for the date
    logger.info(f"--> Reading SawcData.mdb events for {target_date}...")
    sawc_events = mdb_reader.read_sawc_events(target_date)
    alarms = sawc_events.get("alarms", [])
    operations = sawc_events.get("operations", [])
    logins = sawc_events.get("logins", [])

    # Export separate JSON files for Alarms, Operations, and Login/Logout
    alarm_json_path = os.path.join(out_json_dir, f"AlarmHistory-{target_date}.json")
    with open(alarm_json_path, "w", encoding="utf-8") as af:
        json.dump({"date": target_date, "equipmentId": "MC081", "totalAlarms": len(alarms), "alarms": alarms}, af, indent=2, default=str)
    logger.info(f"    Exported AlarmHistory JSON: {alarm_json_path}")

    ops_json_path = os.path.join(out_json_dir, f"OperatingHistory-{target_date}.json")
    with open(ops_json_path, "w", encoding="utf-8") as of:
        json.dump({"date": target_date, "equipmentId": "MC081", "totalOperations": len(operations), "operations": operations}, of, indent=2, default=str)
    logger.info(f"    Exported OperatingHistory JSON: {ops_json_path}")

    login_json_path = os.path.join(out_json_dir, f"LogInOutHistory-{target_date}.json")
    with open(login_json_path, "w", encoding="utf-8") as lf:
        json.dump({"date": target_date, "equipmentId": "MC081", "totalLogins": len(logins), "loginHistory": logins}, lf, indent=2, default=str)
    logger.info(f"    Exported LogInOutHistory JSON: {login_json_path}")

    # Ingest alarms, audits, and logins into MongoDB
    alms_count = mongo_loader.load_alarms(alarms)
    auds_count = mongo_loader.load_audits(operations)
    logins_count = mongo_loader.load_logins(logins)
    logger.info(f"    Ingested Alarms: {alms_count} | Audits: {auds_count} | Logins: {logins_count}")

    # Apply batch limit if configured
    if batch_limit and batch_limit > 0:
        files_to_process = xls_files[:batch_limit]
        logger.info(f"Applying batch limit per run: processing {len(files_to_process)} of {len(xls_files)} reports")
    else:
        files_to_process = xls_files

    batch_results = []
    last_processed_file = ""
    
    # Track sequence of lots per batch across existing DB records + current run
    batch_lot_counters = {}

    # 3. Parse and ingest each ProductionReport-*.xls
    for xls_path in files_to_process:
        fn = os.path.basename(xls_path)
        logger.info(f"--> Processing Production Report: {fn}")
        try:
            # Check if this file was already ingested and has an assigned lot
            existing_doc = None
            if mongo_loader.db is not None:
                existing_doc = mongo_loader.db["iiot_ts_batch_MC081"].find_one({
                    "$or": [
                        {"compression_details.metadata.sourceFile": fn},
                        {"metadata.sourceFile": fn}
                    ]
                })

            # Temporary parse to get batch_no
            temp_report = parse_production_report_xls(xls_path)
            batch_no = temp_report.get("meta", {}).get("batchNo") or "UNKNOWN"

            if existing_doc and (existing_doc.get("meta", {}).get("lotNo") or existing_doc.get("meta", {}).get("derivedLotNo")):
                assigned_lot = existing_doc.get("meta", {}).get("derivedLotNo") or existing_doc.get("meta", {}).get("lotNo")
                if not str(assigned_lot).startswith("Lot-"):
                    if batch_no not in batch_lot_counters:
                        batch_lot_counters[batch_no] = 0
                    batch_lot_counters[batch_no] += 1
                    assigned_lot = f"Lot-{batch_lot_counters[batch_no]:02d}"
            else:
                if batch_no not in batch_lot_counters:
                    existing_count = 0
                    if mongo_loader.db is not None:
                        existing_count = mongo_loader.db["iiot_ts_batch_MC081"].count_documents({
                            "meta.batchNo": batch_no,
                            "compression_details.metadata.sourceFile": {"$ne": fn}
                        })
                    batch_lot_counters[batch_no] = existing_count
                batch_lot_counters[batch_no] += 1
                assigned_lot = f"Lot-{batch_lot_counters[batch_no]:02d}"

            report_data = parse_production_report_xls(xls_path, lot_no_override=assigned_lot)

            # Embed date-level operation, login, and alarm history inside compression_details
            if "compression_details" in report_data:
                report_data["compression_details"]["operation_history"] = operations
                report_data["compression_details"]["login_history"] = logins
                report_data["compression_details"]["alarm_history"] = alarms

            # Export JSON
            json_name = os.path.splitext(fn)[0] + ".json"
            json_out_path = os.path.join(out_json_dir, json_name)
            with open(json_out_path, "w", encoding="utf-8") as jf:
                json.dump(report_data, jf, indent=2, default=str)
            logger.info(f"    Exported JSON: {json_out_path}")

            # Ingest into MongoDB
            mongo_res = mongo_loader.load_batch_report(report_data)
            last_processed_file = fn
            batch_results.append({
                "file": fn,
                "batchNo": batch_no,
                "productName": report_data.get("meta", {}).get("productName"),
                "goodTablets": report_data.get("compression_details", {}).get("tabletCounters", {}).get("good", {}).get("count"),
                "status": "INGESTED"
            })
        except Exception as e:
            logger.error(f"Error processing {fn}: {e}", exc_info=True)
            batch_results.append({"file": fn, "status": "ERROR", "error": str(e)})

    # 4. Update Checkpoint in MongoDB
    max_sawc_id = None
    all_sawc_records = alarms + operations + logins
    if all_sawc_records:
        try:
            ids = [int(x.get("alarmId", x.get("auditId", x.get("loginId", "0"))).split("-")[-1]) for x in all_sawc_records if "-" in x.get("alarmId", x.get("auditId", x.get("loginId", "")))]
            if ids:
                max_sawc_id = max(ids)
        except Exception:
            pass

    mongo_loader.update_checkpoint(
        equipment_code="MC081",
        last_ingested_date=target_date,
        last_report_file=last_processed_file,
        last_sawc_id=max_sawc_id,
        status="COMPLETED"
    )

    summary = {
        "date": target_date,
        "equipmentId": "MC081",
        "reportsProcessed": len(batch_results),
        "batchDetails": batch_results,
        "alarmsIngested": alms_count,
        "auditsIngested": auds_count,
        "loginsIngested": logins_count,
        "completedAt": datetime.now(timezone.utc).isoformat()
    }
    return summary


def main():
    parser = argparse.ArgumentParser(description="Sejong MC081 Compression File Ingestion Engine")
    parser.add_argument("--config", default="config/file_ingestion_config.json", help="Path to configuration file")
    parser.add_argument("--date", help="Specific date to ingest (YYYY-MM-DD)")
    parser.add_argument("--all-dates", action="store_true", help="Ingest all discovered date folders")
    parser.add_argument("--limit", type=int, help="Override maximum batches per date")
    parser.add_argument("--truncate-and-load", action="store_true", help="Reset transactional data before ingestion")
    args = parser.parse_args()

    # Load configuration
    config = load_config(args.config)
    mongo_cfg = config.get("database", {})
    mongo_uri = os.getenv("MONGO_URI", mongo_cfg.get("mongo_uri", "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin"))
    db_name = os.getenv("DB_NAME", mongo_cfg.get("db_name", "adavis_platform"))
    source_dir = os.getenv("LOCAL_SOURCE_PATH", config.get("source", {}).get("local_backup_path", "../compression_server"))
    batch_limit = args.limit or config.get("ingestion", {}).get("max_batches_per_run", None)

    # Initialize core modules
    file_fetcher = FileFetcher(config)
    mdb_reader = SawcMdbReader(base_dir=source_dir)
    mongo_loader = MongoCompressionLoader(mongo_uri=mongo_uri, db_name=db_name)

    if args.truncate_and_load or os.getenv("INGESTION_MODE", "").upper() == "TRUNCATE_AND_LOAD":
        mongo_loader.prepare_database_for_ingestion(ingestion_mode="TRUNCATE_AND_LOAD")

    dates_to_run: List[str] = []

    if args.date:
        dates_to_run = [args.date]
    elif args.all_dates:
        dates_to_run = file_fetcher.discover_dates()
        logger.info(f"Discovered {len(dates_to_run)} dates to ingest: {dates_to_run}")
    else:
        # Check checkpoint first
        cp = mongo_loader.get_checkpoint("MC081")
        all_dates = file_fetcher.discover_dates()
        if cp and cp.get("lastIngestedDate"):
            last_dt = cp.get("lastIngestedDate")
            dates_to_run = [d for d in all_dates if d >= last_dt]
            logger.info(f"Resuming from checkpoint {last_dt}: {len(dates_to_run)} dates to process")
        else:
            dates_to_run = all_dates

    logger.info(f"Target Ingestion Dates: {dates_to_run}")

    all_summaries = []
    for dt in dates_to_run:
        summary = ingest_single_date(dt, config, file_fetcher, mdb_reader, mongo_loader, batch_limit=batch_limit)
        all_summaries.append(summary)

    logger.info("============================================================")
    logger.info("INGESTION COMPLETE SUMMARY:")
    for s in all_summaries:
        logger.info(f"  Date: {s['date']} | Reports: {s['reportsProcessed']} | Alarms: {s['alarmsIngested']} | Audits: {s['auditsIngested']} | Logins: {s['loginsIngested']}")
    logger.info("============================================================")


if __name__ == "__main__":
    main()
