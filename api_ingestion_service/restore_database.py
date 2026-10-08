"""
Restore MongoDB Collections and Data from Disk Export JSON files.
Supports selecting a specific backup folder, filtering collections, and safe restore modes.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
from pathlib import Path

base_dir = Path(__file__).resolve().parent
if str(base_dir) not in sys.path:
    sys.path.insert(0, str(base_dir))

try:
    from api_ingestion_service.core.backup_manager import restore_collections
except ImportError:
    from core.backup_manager import restore_collections

from pymongo import MongoClient

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("restore_database")


def load_env_file() -> None:
    for env_path in [base_dir / ".env", base_dir.parent / ".env"]:
        if env_path.exists():
            with open(env_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        k, v = k.strip(), v.strip().strip("\"'")
                        if k and k not in os.environ:
                            os.environ[k] = v


def find_latest_backup(backups_dir: Path) -> Path | None:
    if not backups_dir.exists():
        return None
    dirs = [d for d in backups_dir.iterdir() if d.is_dir()]
    if not dirs:
        return None
    # Sort by creation time / name (names are timestamped)
    return sorted(dirs, key=lambda d: d.name, reverse=True)[0]


def main() -> None:
    parser = argparse.ArgumentParser(description="Restore MongoDB collections from disk backup folder.")
    parser.add_argument("--backup-dir", help="Path to backup directory (default: latest in ./backups)")
    parser.add_argument("--collections", nargs="*", help="Specific collections to restore (default: all in backup)")
    parser.add_argument("--append", action="store_true", help="Append to existing data instead of replacing")
    args = parser.parse_args()

    load_env_file()

    # Load config for defaults
    config_path = base_dir / "config" / "ingestion_config.json"
    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    db_cfg = config.get("database", {})
    mongo_uri = os.getenv("MONGO_URI") or os.getenv("MONGODB_URI") or db_cfg.get("mongo_uri") or "mongodb://localhost:37017"
    db_name = os.getenv("MONGO_DB_NAME") or os.getenv("MONGODB_DATABASE") or db_cfg.get("db_name", "adavis_platform")

    backup_path = Path(args.backup_dir) if args.backup_dir else find_latest_backup(base_dir / "backups")
    if not backup_path or not backup_path.exists():
        logger.error(f"No backup directory found at '{backup_path}'. Please specify --backup-dir.")
        sys.exit(1)

    logger.info(f"Connecting to MongoDB at '{mongo_uri}' (DB: '{db_name}')...")
    client = MongoClient(mongo_uri, serverSelectionTimeoutMS=5000)
    client.admin.command("ping")
    db = client[db_name]

    logger.info(f"Restoring from backup: {backup_path.resolve()} (Drop Existing: {not args.append})")
    summary = restore_collections(
        db=db,
        backup_dir=backup_path,
        collection_names=args.collections,
        drop_existing=not args.append,
    )

    print("\n" + "=" * 60)
    print("DATABASE RESTORE SUMMARY")
    print("=" * 60)
    print(f"Source Directory: {summary['backup_source']}")
    print(f"Total Collections Restored: {len(summary['collections_restored'])}")
    print(f"Total Documents Restored:   {summary['total_documents_restored']}")
    print("-" * 60)
    for col, count in summary["collections_restored"].items():
        print(f"  - {col:35s} : {count} docs")
    print("=" * 60 + "\n")


if __name__ == "__main__":
    main()
