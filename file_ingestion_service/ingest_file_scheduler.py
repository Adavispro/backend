"""
File Ingestion Scheduler Daemon for Sejong Compression Machine (MC081).
Runs every 15 minutes, discovers missing dates from checkpoint, respects batch limits,
and synchronizes MongoDB (batch runs, alarms, audits, logins, live status, and checkpoints).
"""

import os
import sys
import time
import json
import logging
import argparse
from pathlib import Path
from datetime import datetime, timezone
from typing import Dict, Any, List

from core.file_fetcher import FileFetcher
from core.production_report_parser import parse_production_report_xls
from core.sawc_mdb_reader import SawcMdbReader
from core.mongo_loader import MongoCompressionLoader
from ingest_date_run import ingest_single_date, load_config

BASE_DIR = Path(__file__).resolve().parent
LOG_DIR = BASE_DIR / "logs"
LOG_DIR.mkdir(parents=True, exist_ok=True)
# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(LOG_DIR / "file_ingestion_scheduler.log", encoding="utf-8")
    ]
)
logger = logging.getLogger("compression.scheduler")

STATE_FILE = BASE_DIR / ".state.json"


def load_scheduler_state() -> Dict[str, Any]:
    """Load persistent scheduler state."""
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {
        "lastRunAt": None,
        "processedDates": [],
        "totalRuns": 0
    }


def save_scheduler_state(state: Dict[str, Any]):
    """Save persistent scheduler state."""
    try:
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(state, f, indent=2, default=str)
    except Exception as e:
        logger.error(f"Error saving scheduler state: {e}")


def run_scheduler_cycle(config: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    Runs a single scheduler ingestion pass.
    1. Checks MongoDB checkpoint for MC081.
    2. Identifies dates needing ingestion (from checkpoint up to current).
    3. Respects max_batches_per_run config limit.
    4. Updates checkpoint upon completion.
    """
    mongo_cfg = config.get("database", {})
    mongo_uri = os.getenv("MONGO_URI") or os.getenv("MONGODB_URI") or mongo_cfg.get("mongo_uri") or "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin"
    db_name = os.getenv("MONGO_DB_NAME") or os.getenv("DB_NAME") or mongo_cfg.get("db_name", "adavis_platform")
    configured_source = config.get("source", {}).get("local_backup_path", "../compression_server")
    source_dir = os.getenv("LOCAL_SOURCE_PATH") or str((BASE_DIR / configured_source).resolve())
    batch_limit = config.get("ingestion", {}).get("max_batches_per_run", 5)

    file_fetcher = FileFetcher(config)
    mdb_reader = SawcMdbReader(base_dir=source_dir)
    mongo_loader = MongoCompressionLoader(mongo_uri=mongo_uri, db_name=db_name)

    state = load_scheduler_state()
    all_dates = file_fetcher.discover_dates()

    # Determine date range from checkpoint
    checkpoint = mongo_loader.get_checkpoint("MC081")
    if checkpoint and checkpoint.get("lastIngestedDate"):
        last_dt = checkpoint.get("lastIngestedDate")
        # Include current and newer dates
        dates_to_process = [d for d in all_dates if d >= last_dt]
        logger.info(f"Scheduler: Resuming from checkpoint date '{last_dt}'. Pending dates: {dates_to_process}")
    else:
        start_cfg = config.get("ingestion", {}).get("start_date")
        if start_cfg:
            dates_to_process = [d for d in all_dates if d >= start_cfg]
        else:
            dates_to_process = all_dates

    # Optionally cap up to end_date if provided in config
    end_cfg = config.get("ingestion", {}).get("end_date")
    if end_cfg:
        dates_to_process = [d for d in dates_to_process if d <= end_cfg]

    logger.info(f"Scheduler: Found {len(dates_to_process)} dates to process.")

    summaries = []
    for dt in dates_to_process:
        summary = ingest_single_date(
            target_date=dt,
            config=config,
            file_fetcher=file_fetcher,
            mdb_reader=mdb_reader,
            mongo_loader=mongo_loader,
            batch_limit=batch_limit
        )
        summaries.append(summary)
        if dt not in state["processedDates"]:
            state["processedDates"].append(dt)

    state["lastRunAt"] = datetime.now(timezone.utc).isoformat()
    state["totalRuns"] = state.get("totalRuns", 0) + 1
    save_scheduler_state(state)

    logger.info(f"Scheduler Pass Completed. Processed {len(summaries)} dates.")
    return summaries


def main():
    parser = argparse.ArgumentParser(description="Sejong Compression Ingestion Scheduler")
    parser.add_argument("--config", default=str(BASE_DIR / "config" / "file_ingestion_config.json"), help="Path to configuration file")
    parser.add_argument("--once", action="store_true", help="Run once and exit (for cron/CLI jobs)")
    args = parser.parse_args()

    config = load_config(args.config)
    ing_cfg = config.get("ingestion", {})
    schedule_min = ing_cfg.get("schedule_minutes", 15)
    continuous = not args.once and ing_cfg.get("continuous_run", False)

    logger.info("=================================================================")
    logger.info("Sejong Tablet Press (MC081) File Ingestion Scheduler Initialized")
    logger.info(f"Interval: {schedule_min} minutes | Mode: {'CONTINUOUS' if continuous else 'SINGLE PASS'}")
    logger.info("=================================================================")

    try:
        run_scheduler_cycle(config)

        if continuous:
            logger.info("Continuous mode active. Press Ctrl+C at any time to exit.")
        while continuous:
            logger.info(f"Sleeping for {schedule_min} minutes until next scheduled check (Press Ctrl+C to stop)...")
            end_time = time.time() + (schedule_min * 60)
            while time.time() < end_time:
                time.sleep(0.5)
            try:
                run_scheduler_cycle(config)
            except Exception as e:
                logger.error(f"Error during scheduled cycle: {e}", exc_info=True)
    except KeyboardInterrupt:
        logger.info("\nCtrl+C detected, shutting down File Ingestion Scheduler...")
        sys.exit(0)


if __name__ == "__main__":
    main()

