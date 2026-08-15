#!/usr/bin/env python3
"""Background loop runner for dataset-aware scheduler ingestion."""

from __future__ import annotations

import argparse
import json
import time
from datetime import datetime

from scheduler.ingestion import DEFAULT_DATASET_IDS, SchedulerIngestionService


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run scheduler ingestion cycle in a loop")
    parser.add_argument("--mongo-uri", default="mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin")
    parser.add_argument("--db-name", default="adavis_platform")
    parser.add_argument("--interval-seconds", type=int, default=900)
    parser.add_argument("--dataset-ids", nargs="*", default=list(DEFAULT_DATASET_IDS))
    parser.add_argument("--once", action="store_true", help="Run one ingestion cycle and exit")
    return parser.parse_args()


def run_cycle(service: SchedulerIngestionService, dataset_ids: list[str]) -> None:
    result = service.run_scheduler_cycle(current_time=datetime.now(), dataset_ids=dataset_ids)
    print(json.dumps(result, default=str), flush=True)


def main() -> int:
    args = parse_args()
    service = SchedulerIngestionService(mongo_uri=args.mongo_uri, db_name=args.db_name)

    dataset_ids = [value.strip() for value in args.dataset_ids if str(value).strip()]
    if not dataset_ids:
        dataset_ids = list(DEFAULT_DATASET_IDS)

    if args.once:
        run_cycle(service, dataset_ids)
        return 0

    try:
        while True:
            run_cycle(service, dataset_ids)
            time.sleep(max(1, args.interval_seconds))
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
