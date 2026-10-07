"""Independent entry point for API staging."""

import time

from api.api_fetcher import APIFetcher
from common import load_config, make_logger


def main():
    config = load_config()["api_fetch"]
    logger = make_logger("api_fetch")
    fetcher = APIFetcher(config, logger)
    while True:
        fetcher.run_cycle()
        if not config.get("continuous_fetch", False):
            return
        time.sleep(max(1, float(config["schedule_minutes"]) * 60))


if __name__ == "__main__":
    main()
