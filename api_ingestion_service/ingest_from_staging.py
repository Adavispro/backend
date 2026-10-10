"""
Direct Ingestion from Staged Local JSON Files into MongoDB.
Reads all staged batches, cleans the payload, upserts master records,
writes to time-series collections, and aggregates multi-stage batch summaries.
"""

from __future__ import annotations

import json
import logging
import os
import sys
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Any, Dict, List, Optional

# Ensure module and parent directory resolution for standalone portability
base_dir = Path(__file__).resolve().parent
if str(base_dir) not in sys.path:
    sys.path.insert(0, str(base_dir))
if str(base_dir.parent) not in sys.path:
    sys.path.insert(0, str(base_dir.parent))

try:
    from api_ingestion_service.core.cleaner import clean_batch_info_row, clean_dict, clean_record_list, strip_tags
    from api_ingestion_service.core.mongo_loader import MongoIngestionLoader
    from api_ingestion_service.core.staging_reader import StagingReader
except ImportError:
    from core.cleaner import clean_batch_info_row, clean_dict, clean_record_list, strip_tags
    from core.mongo_loader import MongoIngestionLoader
    from core.staging_reader import StagingReader

# Configure Logging (Console + Rotating File Log)
log_dir = base_dir / "logs"
log_dir.mkdir(parents=True, exist_ok=True)
log_file = log_dir / "ingestion.log"

logger = logging.getLogger("api_ingestion_service.staging_ingest")
logger.setLevel(logging.INFO)

console_handler = logging.StreamHandler(sys.stdout)
console_handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s"))
logger.addHandler(console_handler)

file_handler = RotatingFileHandler(log_file, maxBytes=10 * 1024 * 1024, backupCount=5, encoding="utf-8")
file_handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s"))
logger.addHandler(file_handler)


def load_env_file(env_path: Optional[Path] = None) -> None:
    """Zero-dependency .env loader."""
    if env_path is None:
        env_path = base_dir / ".env"
    if not env_path.exists():
        env_path = base_dir.parent / ".env"
    if not env_path.exists():
        return
    try:
        with open(env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, val = line.split("=", 1)
                key = key.strip()
                val = val.strip().strip("\"'")
                if key and key not in os.environ:
                    os.environ[key] = val
    except Exception:
        pass


def run_staging_ingestion(config_path: str | None = None) -> Dict[str, Any]:
    load_env_file()

    if config_path is None:
        config_path = str(base_dir / "config" / "ingestion_config.json")

    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    # Determine staging root
    sample_data_rel = config["mock_server"].get("sample_data_path", "./sample_data")
    staging_root = (base_dir / sample_data_rel).resolve()
    logger.info(f"Using Staging Directory: {staging_root}")

    # Init MongoDB Loader with env overrides
    db_cfg = config.get("database", {})
    mongo_uri = os.getenv("MONGO_URI") or os.getenv("MONGODB_URI") or db_cfg.get("mongo_uri")
    db_name = os.getenv("MONGO_DB_NAME") or os.getenv("MONGODB_DATABASE") or db_cfg.get("db_name", "adavis_platform")

    loader = MongoIngestionLoader(
        mongo_uri=mongo_uri,
        db_name=db_name,
    )

    # Ingestion Mode & Backup Configuration
    ingest_cfg = config.get("ingestion", {})
    ingestion_mode = os.getenv("INGESTION_MODE", ingest_cfg.get("ingestion_mode", "APPEND")).upper()
    backup_env = os.getenv("BACKUP_BEFORE_TRUNCATE")
    backup_before_truncate = backup_env.lower() in ("true", "1", "yes") if backup_env is not None else ingest_cfg.get("backup_before_truncate", True)

    asset_configs = {a["asset_id"]: a for a in config.get("assets", [])}
    asset_codes = [a.get("code") or a["asset_id"] for a in config.get("assets", [])]
    loader.prepare_database_for_ingestion(
        asset_codes=asset_codes,
        ingestion_mode=ingestion_mode,
        backup_enabled=backup_before_truncate,
    )

    reader = StagingReader(str(staging_root))
    available_assets = reader.get_available_assets()
    logger.info(f"Discovered assets in staging: {available_assets}")

    stats = {
        "processed_assets": 0,
        "processed_batches": 0,
        "operational_events_inserted": 0,
        "alarms_inserted": 0,
        "audits_inserted": 0,
    }

    for asset_id in available_assets:
        asset_info = asset_configs.get(asset_id, {
            "code": asset_id,
            "name": f"Equipment {asset_id}",
            "stage_order": 1,
            "stage_name": "Stage",
            "equipment_type": "RMG",
        })
        equipment_code = asset_info.get("code") or asset_id

        job_run_id = loader.start_job_run(equipment_code)
        batch_info_rows = reader.read_latest_batch_info(asset_id)
        logger.info(f"Asset {asset_id} ({asset_info['name']}, Code: {equipment_code}): found {len(batch_info_rows)} batch entries in Batch_Info")

        asset_batch_count = 0

        global_max = int(os.getenv("MAX_BATCHES_PER_ASSET_PER_CYCLE", os.getenv("MAX_BATCHES_PER_DEVICE", config.get("ingestion", {}).get("max_batches_per_asset_per_cycle", 15))))
        max_batches = int(asset_info.get("max_batches_per_cycle", asset_info.get("max_batches", global_max)))

        # Process each batch in the staging dataset
        for raw_b in batch_info_rows[:max_batches]:
            cleaned_b = clean_batch_info_row(raw_b)
            batch_no = cleaned_b["batch_no"]
            lot_no = cleaned_b["lot_no"]
            product_code = cleaned_b["product_code"]
            product_name = cleaned_b["product_name"]

            if not batch_no:
                continue

            # Ingest ONLY completed batches (require valid start and end timestamps)
            if not cleaned_b.get("is_completed") or not cleaned_b.get("start_time") or not cleaned_b.get("end_time"):
                logger.info(f"Skipping in-flight/incomplete batch {batch_no} (Lot: {lot_no}) on asset {asset_id} - requires valid Start Time and End Time.")
                continue

            # 1. Upsert Product Master
            loader.upsert_product(product_code, product_name)

            # 2. Read staged dataset files for this batch
            datasets = reader.read_batch_datasets(asset_id, batch_no, lot_no)
            if not datasets:
                continue

            # Validate batch_summary dataset end time if present
            bs_key_check = next((k for k in datasets.keys() if "batch_summary" in k or "batch_summ" in k), None)
            if bs_key_check and datasets[bs_key_check]:
                bs_records_check = clean_record_list(datasets[bs_key_check])
                has_incomplete_bs = False
                for bs_row in bs_records_check:
                    bs_et = bs_row.get("End Time") or bs_row.get("End_Time") or bs_row.get("BatchEndDate")
                    if bs_et is None or str(bs_et).strip() in ("", "-", "null", "None", "NULL"):
                        has_incomplete_bs = True
                        break
                if has_incomplete_bs:
                    logger.info(f"Skipping batch {batch_no} (Lot: {lot_no}) on asset {asset_id} - batch_summary dataset has null/missing End Time.")
                    continue

            # Resolve dynamic Equipment ID from machine_summary dataset if present
            current_equipment_code = equipment_code
            mach_key = next((k for k in datasets.keys() if "machine_summary" in k or "mach_summary" in k), None)
            if mach_key and datasets[mach_key]:
                mach_records = clean_record_list(datasets[mach_key])
                for m_row in mach_records:
                    eq_id = m_row.get("Equipment ID") or m_row.get("equipment_id") or m_row.get("Equipment_ID") or m_row.get("EquipmentId")
                    if eq_id:
                        current_equipment_code = str(eq_id).strip()
                        break

            # Idempotency check: if data already ingested, no need to reingest
            if loader.is_batch_stage_ingested(batch_no, lot_no, current_equipment_code, asset_info.get("equipment_type", "RMG")):
                logger.info(f"Batch {batch_no} (Lot: {lot_no}, Equipment: {current_equipment_code}) already ingested. Skipping re-ingestion.")
                continue

            asset_batch_count += 1
            op_event_docs: List[Dict[str, Any]] = []

            # Extract users and audit trail to identify supervisor and operator
            supervisor_name = ""
            operator_name = ""
            users_list: List[Dict[str, Any]] = []
            if "users" in datasets and datasets["users"]:
                users_list = clean_record_list(datasets["users"])
            elif "login_logout" in datasets and datasets["login_logout"]:
                users_list = clean_record_list(datasets["login_logout"])

            for u in users_list:
                uname = str(u.get("User Name") or u.get("user_name") or "")
                if not supervisor_name and "supervisor" in uname.lower():
                    supervisor_name = uname
                if not operator_name and "operator" in uname.lower():
                    operator_name = uname

            audit_records: List[Dict[str, Any]] = []
            if "audit_trail" in datasets and datasets["audit_trail"]:
                audit_records = clean_record_list(datasets["audit_trail"])
                audit_docs = loader.sync_audits(
                    asset_code=current_equipment_code,
                    batch_no=batch_no,
                    lot_no=lot_no,
                    records=audit_records,
                )
                stats["audits_inserted"] += len(audit_docs)
                if not operator_name:
                    for a in audit_records:
                        uname = str(a.get("User Name") or a.get("user_name") or "")
                        if "operator" in uname.lower():
                            operator_name = uname
                            break

            # 3. Process Operational Data
            op_key = next((k for k in datasets.keys() if "operational_data" in k or "op_data" in k), None)
            if op_key and datasets[op_key]:
                op_records = clean_record_list(datasets[op_key])
                op_event_docs = loader.sync_operational_events(
                    asset_code=current_equipment_code,
                    equipment_type=asset_info.get("equipment_type", "RMG"),
                    batch_no=batch_no,
                    lot_no=lot_no,
                    records=op_records,
                    default_status=cleaned_b.get("status") or "RUNNING",
                    default_operator=operator_name,
                    users_records=users_list,
                    audit_records=audit_records,
                )
                stats["operational_events_inserted"] += len(op_event_docs)

            # 4. Process Alarms
            if "alarms" in datasets and datasets["alarms"]:
                alarm_records = clean_record_list(datasets["alarms"])
                alarm_docs = loader.sync_alarms(asset_code=current_equipment_code, records=alarm_records)
                stats["alarms_inserted"] += len(alarm_docs)

            # 5. Process Recipe
            recipe_key = next((k for k in datasets.keys() if "recipe" in k), None)
            if recipe_key and datasets[recipe_key]:
                loader.upsert_recipe(
                    asset_code=current_equipment_code,
                    batch_no=batch_no,
                    lot_no=lot_no,
                    recipe_records=datasets[recipe_key],
                )

            # Extract batch size and recipe name from batch_summary dataset if present
            batch_size = None
            recipe_name = None
            bs_key = next((k for k in datasets.keys() if "batch_summary" in k or "batch_summ" in k), None)
            if bs_key and datasets[bs_key]:
                bs_records = clean_record_list(datasets[bs_key])
                for bs_row in bs_records:
                    bs_val = bs_row.get("Batch Size") or bs_row.get("batch_size") or bs_row.get("BatchSize")
                    if bs_val:
                        batch_size = str(bs_val).strip()
                    rn_val = bs_row.get("Recipe Name") or bs_row.get("recipe_name") or bs_row.get("RecipeName")
                    if rn_val:
                        recipe_name = str(rn_val).strip()

            # 6. Aggregate & Upsert Multi-Stage Batch Summary
            loader.upsert_batch_summary(
                batch_no=batch_no,
                lot_no=lot_no,
                product_name=product_name,
                product_code=product_code,
                asset_code=current_equipment_code,
                stage_order=asset_info.get("stage_order", 1),
                stage_name=asset_info.get("stage_name", "Granulation"),
                equipment_type=asset_info.get("equipment_type", "RMG"),
                op_event_docs=op_event_docs,
                stage_status=cleaned_b["status"],
                operator_name=operator_name,
                supervisor_name=supervisor_name,
                batch_size=batch_size,
                recipe_name=recipe_name,
            )

            # 7. Register completed batch stage to prevent re-ingestion
            loader.mark_batch_stage_ingested(
                batch_no=batch_no,
                lot_no=lot_no,
                asset_code=current_equipment_code,
                equipment_type=asset_info.get("equipment_type", "RMG"),
            )

        loader.finish_job_run(job_run_id, status="SUCCESS", processed_batches=asset_batch_count)
        stats["processed_assets"] += 1
        stats["processed_batches"] += asset_batch_count

    logger.info(f"Ingestion from staging completed. Stats: {stats}")
    return stats


if __name__ == "__main__":
    run_staging_ingestion()
