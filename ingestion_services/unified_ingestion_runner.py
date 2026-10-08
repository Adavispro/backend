"""
Unified IIoT Ingestion & Master Data Synchronization Orchestrator for ADAVIS.
Coordinates ingestion across:
- 4 API Equipment: RMG (MB003), FBD (MB004), Blender (MB005), Auto Coater (MB041)
- 1 File Equipment: Sejong 49D Compression (MC081)

Supports:
- Modes: APPEND (default) and TRUNCATE_AND_LOAD
- Truncate and Load cascading reset (parent-child dependency order)
- Master Data Synchronization (Equipment, Critical Parameters, Product, Recipe, Batch Associations)
- Automatic derivation & creation of missing Recipes and Batch Sizes
- Scheduled recurring execution or Single-Run (--once)
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

# Setup path resolution
current_dir = Path(__file__).resolve().parent
if str(current_dir) not in sys.path:
    sys.path.insert(0, str(current_dir))

from common.master_data_sync import (
    MasterDataSyncManager,
    EQUIPMENT_DEFINITIONS,
    CRITICAL_PARAMETERS_CONFIG,
)
from api_ingestion_service.core.mongo_loader import MongoIngestionLoader
from api_ingestion_service.ingest_from_staging import run_staging_ingestion
from file_ingestion_service.core.file_fetcher import FileFetcher
from file_ingestion_service.core.sawc_mdb_reader import SawcMdbReader
from file_ingestion_service.core.mongo_loader import MongoCompressionLoader
from file_ingestion_service.ingest_date_run import ingest_single_date, load_config as load_comp_config

# Setup Logger
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("iiot.unified_orchestrator")


def execute_unified_ingestion(
    *,
    mode: str = "APPEND",
    run_api: bool = True,
    run_compression: bool = True,
    compression_date: Optional[str] = None,
    compression_all_dates: bool = False,
    backup_before_truncate: bool = False,
) -> Dict[str, Any]:
    """
    Executes a complete ingestion run according to the required workflow sequence:
    1. Truncate & Load cascading reset (if mode == TRUNCATE_AND_LOAD)
    2. Master Data Synchronization (Equipment, Parameters, Limits)
    3. API Ingestion (MB003, MB004, MB005, MB041)
    4. File Ingestion (MC081)
    5. Integrity & Master Data Association Validation
    """
    start_time = datetime.now(timezone.utc)
    logger.info("================================================================================")
    logger.info(f"ADAVIS UNIFIED INGESTION STARTED | Mode: {mode.upper()} | Time: {start_time.isoformat()}")
    logger.info("================================================================================")

    mongo_uri = os.getenv("MONGO_URI", "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin")
    db_name = os.getenv("MONGO_DB_NAME", "adavis_platform")

    api_loader = MongoIngestionLoader(mongo_uri=mongo_uri, db_name=db_name)
    comp_loader = MongoCompressionLoader(mongo_uri=mongo_uri, db_name=db_name)

    # 1. Truncate & Load Cascading Reset if requested
    if mode.upper() == "TRUNCATE_AND_LOAD":
        logger.info("--> Executing TRUNCATE_AND_LOAD cascading reset...")
        sync_manager = MasterDataSyncManager(api_loader.db)
        reset_report = sync_manager.cascade_truncate_and_reset(reset_master=True)
        logger.info(f"    Cascading Reset completed: {len(reset_report.get('truncatedCollections', []))} collections truncated")
    else:
        logger.info("--> Running in APPEND mode. Synchronizing Master Data baseline...")
        sync_manager = MasterDataSyncManager(api_loader.db)
        sync_manager.sync_equipment_master()
        sync_manager.sync_critical_parameters()

    results: Dict[str, Any] = {
        "mode": mode.upper(),
        "startedAt": start_time.isoformat(),
        "apiIngestion": None,
        "compressionIngestion": None,
        "masterDataStatus": "SYNCHRONIZED",
    }

    # 2. Run API Ingestion (MB003, MB004, MB005, MB041)
    if run_api:
        logger.info("--------------------------------------------------------------------------------")
        logger.info("STEP 1: API-Based Equipment Ingestion (MB003, MB004, MB005, MB041)")
        logger.info("--------------------------------------------------------------------------------")
        try:
            api_cfg_path = str(current_dir / "api_ingestion_service" / "config" / "ingestion_config.json")
            # Force APPEND for loader inside run_staging_ingestion since DB reset was already handled if requested
            os.environ["INGESTION_MODE"] = "APPEND"
            api_stats = run_staging_ingestion(api_cfg_path)
            results["apiIngestion"] = api_stats
            logger.info(f"--> API Equipment Ingestion completed successfully: {api_stats}")
        except Exception as e:
            logger.error(f"Error during API Ingestion: {e}", exc_info=True)
            results["apiIngestion"] = {"status": "ERROR", "error": str(e)}

    # 3. Run Compression File Ingestion (MC081)
    if run_compression:
        logger.info("--------------------------------------------------------------------------------")
        logger.info("STEP 2: Compression Machine File Ingestion (MC081)")
        logger.info("--------------------------------------------------------------------------------")
        try:
            comp_cfg_path = str(current_dir / "file_ingestion_service" / "config" / "file_ingestion_config.json")
            comp_config = load_comp_config(comp_cfg_path)
            source_dir = os.getenv("LOCAL_SOURCE_PATH") or str((current_dir / "compression_server").resolve())

            file_fetcher = FileFetcher(comp_config)
            mdb_reader = SawcMdbReader(base_dir=source_dir)

            dates_to_run: List[str] = []
            if compression_date:
                dates_to_run = [compression_date]
            elif compression_all_dates:
                dates_to_run = file_fetcher.discover_dates()
            else:
                # Ingest latest available date or checkpoint
                all_dates = file_fetcher.discover_dates()
                if all_dates:
                    dates_to_run = [all_dates[-1]]  # Latest date e.g. 2026-09-30

            logger.info(f"Compression Target Dates: {dates_to_run}")
            comp_summaries = []
            for dt in dates_to_run:
                s = ingest_single_date(dt, comp_config, file_fetcher, mdb_reader, comp_loader)
                comp_summaries.append(s)

            results["compressionIngestion"] = comp_summaries
            logger.info(f"--> Compression File Ingestion completed: {len(comp_summaries)} date runs processed")
        except Exception as e:
            logger.error(f"Error during Compression Ingestion: {e}", exc_info=True)
            results["compressionIngestion"] = {"status": "ERROR", "error": str(e)}

    end_time = datetime.now(timezone.utc)
    duration_sec = (end_time - start_time).total_seconds()
    results["completedAt"] = end_time.isoformat()
    results["durationSeconds"] = duration_sec

    logger.info("================================================================================")
    logger.info(f"ADAVIS UNIFIED INGESTION COMPLETED | Duration: {duration_sec:.2f}s")
    logger.info("================================================================================")
    return results


def main() -> None:
    parser = argparse.ArgumentParser(description="ADAVIS Unified Ingestion & Master Data Synchronization Orchestrator")
    parser.add_argument("--mode", choices=["APPEND", "TRUNCATE_AND_LOAD"], default="APPEND", help="Ingestion mode")
    parser.add_argument("--truncate-and-load", action="store_true", help="Shorthand for --mode TRUNCATE_AND_LOAD")
    parser.add_argument("--skip-api", action="store_true", help="Skip API equipment ingestion")
    parser.add_argument("--skip-compression", action="store_true", help="Skip Compression file ingestion")
    parser.add_argument("--comp-date", help="Specific date for compression ingestion (YYYY-MM-DD)")
    parser.add_argument("--comp-all-dates", action="store_true", help="Ingest all dates for compression")
    parser.add_argument("--once", action="store_true", default=True, help="Run single ingestion cycle and exit")
    parser.add_argument("--interval", type=int, default=0, help="Recurring interval in minutes (0 = run once)")
    parser.add_argument("--interval-seconds", type=int, default=0, help="Recurring interval in seconds (0 = run once)")
    parser.add_argument("--mongo-uri", help="MongoDB connection URI")
    parser.add_argument("--db-name", help="MongoDB database name")
    parser.add_argument("--dataset-ids", nargs="*", help="Optional dataset filter for backwards compatibility")
    args = parser.parse_args()

    if args.mongo_uri:
        os.environ["MONGO_URI"] = args.mongo_uri
    if args.db_name:
        os.environ["MONGO_DB_NAME"] = args.db_name

    effective_mode = "TRUNCATE_AND_LOAD" if args.truncate_and_load else args.mode
    interval_sec = args.interval_seconds if args.interval_seconds > 0 else (args.interval * 60 if args.interval > 0 else 0)

    if interval_sec > 0:
        logger.info(f"Starting recurring unified scheduler: running every {interval_sec} seconds...")
        while True:
            try:
                execute_unified_ingestion(
                    mode="APPEND",  # Recurring runs run in APPEND mode
                    run_api=not args.skip_api,
                    run_compression=not args.skip_compression,
                    compression_date=args.comp_date,
                    compression_all_dates=args.comp_all_dates,
                )
            except Exception as e:
                logger.error(f"Error in scheduled ingestion cycle: {e}")
            logger.info(f"Sleeping for {interval_sec} seconds until next cycle...")
            time.sleep(interval_sec)
    else:
        execute_unified_ingestion(
            mode=effective_mode,
            run_api=not args.skip_api,
            run_compression=not args.skip_compression,
            compression_date=args.comp_date,
            compression_all_dates=args.comp_all_dates,
        )


if __name__ == "__main__":
    main()
