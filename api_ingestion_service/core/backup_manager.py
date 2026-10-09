"""
Database Export and Restore Manager for MongoDB Collections.
Provides full JSON/BSON file-based export and restore functionality,
preserving datetime, ObjectId, and nested documents.
"""

from __future__ import annotations

import json
import logging
import os
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

try:
    from bson import json_util
    from pymongo import MongoClient
except ImportError:
    json_util = None
    MongoClient = None

logger = logging.getLogger("api_ingestion_service.backup_manager")


def export_collections(
    db: Any,
    collection_names: Optional[List[str]] = None,
    backup_root: Optional[Path] = None,
    prefix: str = "backup",
) -> Dict[str, Any]:
    """
    Export specified (or all) collections to disk as formatted JSON files.
    
    Args:
        db: PyMongo Database object
        collection_names: Optional list of collection names to export. If None, exports all collections.
        backup_root: Directory to store backup folders (defaults to ./backups)
        prefix: Subfolder prefix (e.g. 'backup', 'truncate_backup')
    
    Returns:
        Dict with export metadata including folder path, counts, and status.
    """
    if db is None:
        raise ValueError("MongoDB database connection is required for export.")

    if backup_root is None:
        backup_root = Path(__file__).resolve().parent.parent / "backups"

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    export_dir = backup_root / f"{prefix}_{timestamp}"
    export_dir.mkdir(parents=True, exist_ok=True)

    available_cols = set(db.list_collection_names())
    # Exclude system collections
    available_cols = {c for c in available_cols if not c.startswith("system.")}

    if collection_names:
        target_cols = [c for c in collection_names if c in available_cols]
    else:
        target_cols = sorted(list(available_cols))

    manifest: Dict[str, Any] = {
        "timestamp": datetime.now(timezone.utc).isoformat() + "Z",
        "database": db.name,
        "backup_directory": str(export_dir.resolve()),
        "collections_exported": {},
        "total_documents": 0,
    }

    for col_name in target_cols:
        col = db[col_name]
        docs = list(col.find({}))
        count = len(docs)

        file_path = export_dir / f"{col_name}.json"
        if json_util is not None:
            json_str = json_util.dumps(docs, indent=2)
        else:
            json_str = json.dumps(docs, indent=2, default=str)

        with open(file_path, "w", encoding="utf-8") as f:
            f.write(json_str)

        manifest["collections_exported"][col_name] = {
            "file": f"{col_name}.json",
            "document_count": count,
            "size_bytes": file_path.stat().st_size,
        }
        manifest["total_documents"] += count
        logger.info(f"Exported {count} documents from '{col_name}' -> {file_path.name}")

    # Write manifest metadata
    manifest_path = export_dir / "manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    logger.info(f"Export completed: {manifest['total_documents']} documents across {len(target_cols)} collections saved to {export_dir}")
    return manifest


def restore_collections(
    db: Any,
    backup_dir: Path,
    collection_names: Optional[List[str]] = None,
    drop_existing: bool = True,
) -> Dict[str, Any]:
    """
    Restore collections from an exported backup directory into MongoDB.
    
    Args:
        db: PyMongo Database object
        backup_dir: Path to directory containing exported .json files
        collection_names: Optional filter of specific collections to restore
        drop_existing: If True, clears/drops the collection before inserting restored data
    
    Returns:
        Dict with restore summary and document counts.
    """
    if db is None:
        raise ValueError("MongoDB database connection is required for restore.")

    backup_dir = Path(backup_dir)
    if not backup_dir.exists() or not backup_dir.is_dir():
        raise FileNotFoundError(f"Backup directory '{backup_dir}' not found.")

    json_files = list(backup_dir.glob("*.json"))
    json_files = [f for f in json_files if f.name != "manifest.json"]

    summary: Dict[str, Any] = {
        "timestamp": datetime.now(timezone.utc).isoformat() + "Z",
        "database": db.name,
        "backup_source": str(backup_dir.resolve()),
        "collections_restored": {},
        "total_documents_restored": 0,
    }

    for fpath in json_files:
        col_name = fpath.stem
        if collection_names and col_name not in collection_names:
            continue

        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read()

        if not content.strip():
            continue

        if json_util is not None:
            docs = json_util.loads(content)
        else:
            docs = json.loads(content)

        if not isinstance(docs, list):
            docs = [docs]

        col = db[col_name]
        if drop_existing:
            col.delete_many({})

        inserted_count = 0
        if docs:
            result = col.insert_many(docs)
            inserted_count = len(result.inserted_ids)

        summary["collections_restored"][col_name] = inserted_count
        summary["total_documents_restored"] += inserted_count
        logger.info(f"Restored {inserted_count} documents into '{col_name}' from {fpath.name}")

    logger.info(f"Restore completed: {summary['total_documents_restored']} documents restored from {backup_dir}")
    return summary
