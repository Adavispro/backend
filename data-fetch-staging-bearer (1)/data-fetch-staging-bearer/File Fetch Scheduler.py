"""Independent entry point for Compression file staging."""

import time

from common import load_config, make_logger
from file.file_fetcher import FileFetcher


def main():
    config = load_config()["file_fetch"]
    logger = make_logger("file_fetch")
    fetcher = FileFetcher(config, logger)
    while True:
        fetcher.run_cycle()
        if not config.get("continuous_fetch", False):
            return
        time.sleep(max(1, float(config["schedule_minutes"]) * 60))


if __name__ == "__main__":
    main()
