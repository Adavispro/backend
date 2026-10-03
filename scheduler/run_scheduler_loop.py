#!/usr/bin/env python3
"""Background loop runner for dataset-aware scheduler ingestion."""

from __future__ import annotations

import argparse
import json
import os
import time
from datetime import datetime

from scheduler.ingestion import DEFAULT_DATASET_IDS, SchedulerIngestionService


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run scheduler ingestion cycle in a loop")
    parser.add_argument(
        "--mongo-uri",
        default=os.getenv("MONGO_URI", "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin"),
    )
    parser.add_argument("--db-name", default=os.getenv("DB_NAME", "adavis_platform"))
    parser.add_argument(
        "--interval-seconds",
        type=int,
        default=int(os.getenv("SCHEDULER_INTERVAL_SECONDS", "10")),
        help="Interval between runs in seconds (default: 10 seconds for continuous telemetry)",
    )
    parser.add_argument("--dataset-ids", nargs="*", default=list(DEFAULT_DATASET_IDS))
    parser.add_argument("--once", action="store_true", help="Run one ingestion cycle and exit")
    return parser.parse_args()


def run_cycle(service: SchedulerIngestionService, dataset_ids: list[str], cycle_count: int = 0) -> None:
    cycle_start = datetime.now()
    result = service.run_scheduler_cycle(current_time=cycle_start, dataset_ids=dataset_ids)

    # Structured one-line summary
    ok_datasets = result.get("successful_datasets", 0)
    fail_datasets = result.get("failed_datasets", 0)
    compression = result.get("compression_watcher") or {}
    comp_events = compression.get("ingested_events", 0) if compression else 0

    batch_totals = sum(
        int((br or {}).get("processed_batches", 0))
        for br in (result.get("batch_results") or [])
    )

    errors_str = ""
    if fail_datasets > 0:
        errs = [f"{e.get('dataset_id')}:{e.get('error', '')[:60]}" for e in (result.get("dataset_errors") or [])]
        errors_str = f"  ERRORS: {'; '.join(errs)}"

    elapsed_ms = int((datetime.now() - cycle_start).total_seconds() * 1000)
    print(
        f"[CYCLE #{cycle_count:04d} {cycle_start.strftime('%H:%M:%S')}] "
        f"datasets={','.join(dataset_ids)} "
        f"ok={ok_datasets}/{len(dataset_ids)} fail={fail_datasets} "
        f"batch_events={batch_totals} comp_events={comp_events} "
        f"elapsed={elapsed_ms}ms"
        f"{errors_str}",
        flush=True,
    )

    # Also emit full JSON for log-file consumers / monitoring
    print(json.dumps(result, default=str), flush=True)


def main() -> int:
    args = parse_args()

    print(
        f"[ADAVIS Scheduler] Starting continuous ingestion loop | "
        f"interval={args.interval_seconds}s | "
        f"datasets={args.dataset_ids} | "
        f"db={args.db_name}",
        flush=True,
    )

    service = SchedulerIngestionService(mongo_uri=args.mongo_uri, db_name=args.db_name)

    dataset_ids = [value.strip() for value in args.dataset_ids if str(value).strip()]
    if not dataset_ids:
        dataset_ids = list(DEFAULT_DATASET_IDS)

    if args.once:
        run_cycle(service, dataset_ids, cycle_count=1)
        return 0

    cycle_count = 0
    try:
        while True:
            cycle_count += 1
            run_cycle(service, dataset_ids, cycle_count=cycle_count)
            time.sleep(max(1, args.interval_seconds))
    except KeyboardInterrupt:
        print(f"[ADAVIS Scheduler] Stopped after {cycle_count} cycles.", flush=True)
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
