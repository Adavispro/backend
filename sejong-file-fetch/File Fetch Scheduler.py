"""Date-aware Sejong Compression file and Access-data staging."""

import argparse
import json
import logging
import os
import time
from pathlib import Path

from sejong_fetch import SejongFetcher


ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description="Stage Sejong Compression files for one date or date range")
    choice = parser.add_mutually_exclusive_group()
    choice.add_argument("--date", help="Exact source date, YYYY-MM-DD")
    choice.add_argument("--today", action="store_true", help="Use today's date")
    choice.add_argument("--from-date", help="First date in an inclusive range, YYYY-MM-DD")
    parser.add_argument("--to-date", help="Last date in an inclusive range, YYYY-MM-DD")
    parser.add_argument("--source-path", help="Override the configured source folder")
    args = parser.parse_args()
    if bool(args.from_date) != bool(args.to_date):
        parser.error("--from-date and --to-date must be used together")
    config_path = Path(os.environ.get("SEJONG_FETCH_CONFIG", ROOT / "config" / "file_fetch_config.json"))
    with config_path.open(encoding="utf-8") as handle:
        config = json.load(handle)
    if args.date:
        config.update(date_mode="EXACT", date=args.date)
    elif args.today:
        config["date_mode"] = "TODAY"
    elif args.from_date:
        config.update(date_mode="RANGE", start_date=args.from_date, end_date=args.to_date)
    if args.source_path:
        config["source_path"] = args.source_path
    log_dir = ROOT / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    logger = logging.getLogger("sejong_file_fetch")
    logger.setLevel(logging.INFO)
    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    for handler in (logging.StreamHandler(), logging.FileHandler(log_dir / "file_fetch.log", encoding="utf-8")):
        handler.setFormatter(formatter)
        logger.addHandler(handler)
    fetcher = SejongFetcher(config, ROOT, logger)
    while True:
        fetcher.run_cycle()
        if not config.get("continuous_fetch", False):
            return
        time.sleep(max(1, float(config.get("schedule_minutes", 20)) * 60))


if __name__ == "__main__":
    main()
