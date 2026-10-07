"""Discover Compression files under COMMON, DAILY, or AUTO layouts."""

from datetime import datetime
from pathlib import Path


def discover_files(network_path, grouping, daily_format):
    root = Path(network_path)
    if not root.is_dir():
        raise FileNotFoundError(f"Compression network path is unavailable: {root}")
    mode = grouping.upper()
    if mode == "COMMON":
        return [path for path in root.iterdir() if path.is_file()]
    if mode == "DAILY":
        files = []
        for folder in root.iterdir():
            if not folder.is_dir():
                continue
            try:
                datetime.strptime(folder.name, daily_format)
            except ValueError:
                continue
            files.extend(path for path in folder.rglob("*") if path.is_file())
        return files
    if mode == "AUTO":
        return [path for path in root.rglob("*") if path.is_file()]
    raise ValueError("folder_grouping must be AUTO, COMMON, or DAILY")
