"""
Export MongoDB Collections and Data to Disk.
Saves collection contents as formatted JSON files with BSON-preserving types.
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
    from api_ingestion_service.core.backup_manager import export_collections
except ImportError:
    from core.backup_manager import export_collections

from pymongo import MongoClient

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("export_database")


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


def main() -> None:
    parser = argparse.ArgumentParser(description="Export MongoDB collections to disk JSON files.")
    parser.add_argument("--collections", nargs="*", help="Specific collection names to export (default: all)")
    parser.add_argument("--output-dir", help="Target backup root directory (default: ./backups)")
    parser.add_argument("--prefix", default="export", help="Backup directory prefix")
    args = parser.parse_args()

    load_env_file()

    # Load config for defaults
    config_path = base_dir / "config" / "ingestion_config.json"
    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    db_cfg = config.get("database", {})
    mongo_uri = os.getenv("MONGO_URI") or os.getenv("MONGODB_URI") or db_cfg.get("mongo_uri") or "mongodb://localhost:37017"
    db_name = os.getenv("MONGO_DB_NAME") or os.getenv("MONGODB_DATABASE") or db_cfg.get("db_name", "adavis_platform")

    logger.info(f"Connecting to MongoDB at '{mongo_uri}' (DB: '{db_name}')...")
    client = MongoClient(mongo_uri, serverSelectionTimeoutMS=5000)
    client.admin.command("ping")
    db = client[db_name]

    backup_root = Path(args.output_dir) if args.output_dir else base_dir / "backups"
    manifest = export_collections(
        db=db,
        collection_names=args.collections,
        backup_root=backup_root,
        prefix=args.prefix,
    )

    print("\n" + "=" * 60)
    print("DATABASE EXPORT SUMMARY")
    print("=" * 60)
    print(f"Destination: {manifest['backup_directory']}")
    print(f"Total Collections: {len(manifest['collections_exported'])}")
    print(f"Total Documents:   {manifest['total_documents']}")
    print("-" * 60)
    for col, info in manifest["collections_exported"].items():
        print(f"  - {col:35s} : {info['document_count']} docs ({info['size_bytes']} bytes)")
    print("=" * 60 + "\n")


if __name__ == "__main__":
    main()
