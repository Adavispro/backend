"""Shared configuration, atomic metadata writes, and scheduler logging."""

import json
import logging
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent


def utc_now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def load_config():
    config_path = Path(os.environ.get("ADAVIS_FETCH_CONFIG", ROOT / "config" / "fetch_config.json"))
    with config_path.open(encoding="utf-8") as handle:
        return json.load(handle)


def project_path(value):
    path = Path(value)
    return path if path.is_absolute() else ROOT / path


def read_json(path, default):
    if not path.exists():
        return default
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def atomic_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="wb", dir=path.parent, prefix=".staging-", delete=False) as handle:
        temp = Path(handle.name)
        handle.write(data)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temp, path)


def write_json(path, value):
    atomic_write(path, (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))


def make_logger(name):
    log_folder = ROOT / "logs"
    log_folder.mkdir(parents=True, exist_ok=True)
    logger = logging.getLogger(name)
    if logger.handlers:
        return logger
    logger.setLevel(os.environ.get("ADAVIS_LOG_LEVEL", "INFO").upper())
    formatter = logging.Formatter('%(asctime)s %(levelname)s %(name)s %(message)s')
    for handler in (logging.StreamHandler(), logging.FileHandler(log_folder / f"{name}.log", encoding="utf-8")):
        handler.setFormatter(formatter)
        logger.addHandler(handler)
    return logger
