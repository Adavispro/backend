#!/usr/bin/env python3
"""Background loop runner for 5-equipment continuous scheduler ingestion."""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from datetime import datetime
from pathlib import Path

backend_dir = Path(__file__).resolve().parent.parent
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from scheduler.config import get_config
from scheduler.ingestion import DEFAULT_DATASET_IDS, SchedulerIngestionService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("scheduler_loop")


def parse_args() -> argparse.Namespace:
    cfg = get_config()
    default_interval = int(os.getenv("INGESTION_INTERVAL_SECONDS", str(cfg.schedule.interval_seconds)))
    default_mode = os.getenv("INGESTION_MODE", cfg.execution_mode).upper()

    parser = argparse.ArgumentParser(description="Run 5-equipment continuous scheduler ingestion loop")
    parser.add_argument(
        "--mongo-uri",
        default=os.getenv("MONGODB_URI", cfg.database.mongo_uri),
        help="MongoDB connection URI",
    )
    parser.add_argument("--db-name", default=os.getenv("MONGODB_DATABASE", cfg.database.mongo_database), help="Database name")
    parser.add_argument(
        "--interval-seconds",
        type=int,
        default=default_interval,
        help="Interval between runs in seconds (default: 10 seconds for real-time telemetry streaming)",
    )
    parser.add_argument(
        "--interval-minutes",
        type=int,
        default=None,
        help="Interval between runs in minutes (e.g. 15 for production continuous schedule)",
    )
    parser.add_argument(
        "--dataset-ids",
        nargs="*",
        default=list(DEFAULT_DATASET_IDS),
        help="Target equipment dataset IDs (default: MB003 MB004 MB005 MB041 MB040)",
    )
    parser.add_argument(
        "--compression-dir",
        default=os.getenv("COMPRESSION_SOURCE_PATH", "data/ingestion/compression"),
        help="Watch directory for compression machine Sejong Excel/Access files",
    )
    parser.add_argument(
        "--execution-mode",
        choices=["ONCE", "CONTINUOUS"],
        default=default_mode,
        help="Execution mode: ONCE for single-pass ingestion, CONTINUOUS for periodic streaming",
    )
    parser.add_argument("--once", action="store_true", help="Run one ingestion cycle and exit (overrides --execution-mode)")
    return parser.parse_args()


def run_cycle(service: SchedulerIngestionService, dataset_ids: list[str]) -> dict:
    start_time = time.time()
    result = service.run_scheduler_cycle(current_time=datetime.now(), dataset_ids=dataset_ids)
    duration = time.time() - start_time
    logger.info(
        "Ingestion cycle completed in %.2fs: successful=%d, failed=%d",
        duration,
        result.get("successful_datasets", 0),
        result.get("failed_datasets", 0),
    )
    return result


def main() -> int:
    args = parse_args()
    logger.info("Initializing Scheduler Ingestion Service for targets: %s", args.dataset_ids)
    logger.info("MongoDB URI: %s (db: %s)", args.mongo_uri, args.db_name)
    logger.info("Compression watch directory: %s", args.compression_dir)

    service = SchedulerIngestionService(
        mongo_uri=args.mongo_uri,
        db_name=args.db_name,
        compression_dir=args.compression_dir,
    )

    dataset_ids = [value.strip() for value in args.dataset_ids if str(value).strip()]
    if not dataset_ids:
        dataset_ids = list(DEFAULT_DATASET_IDS)

    is_once = args.once or (args.execution_mode == "ONCE")

    if is_once:
        logger.info("Executing single ingestion cycle (ONCE mode)...")
        result = run_cycle(service, dataset_ids)
        print(json.dumps(result, default=str, indent=2), flush=True)
        return 0

    effective_interval = (args.interval_minutes * 60) if args.interval_minutes is not None else max(1, args.interval_seconds)
    logger.info("Starting continuous ingestion loop with interval: %ds (%.1f min)", effective_interval, effective_interval / 60.0)
    cycle_count = 0
    try:
        while True:
            cycle_count += 1
            logger.info("--- Cycle #%d starting at %s ---", cycle_count, datetime.now().isoformat())
            run_cycle(service, dataset_ids)
            time.sleep(effective_interval)
    except KeyboardInterrupt:
        logger.info("Scheduler ingestion loop interrupted by user.")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
