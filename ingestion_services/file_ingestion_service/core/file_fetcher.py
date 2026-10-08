"""
File Fetcher and Staging Engine for Sejong Compression Machine.
Discovers date-wise production folders, copies files to local staging,
and manages incremental file synchronization.
"""

from __future__ import annotations

import hashlib
import json
import logging
import os
import re
import shutil
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

logger = logging.getLogger("compression_server.file_fetcher")

DATE_FOLDER_REGEX = re.compile(r"^\d{4}-\d{2}-\d{2}$")
DATE_IN_NAME_REGEX = re.compile(r"(?<!\d)(\d{4}-\d{2}-\d{2})(?!\d)")
ALLOWED_EXTENSIONS = {".xls", ".xlsx", ".mdb", ".accdb", ".csv"}


def compute_file_sha256(filepath: Path) -> str:
    """Compute SHA-256 hash of a file."""
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


class CompressionFileFetcher:
    def __init__(
        self,
        source_path: Any = ".",
        staging_raw_dir: Any = "./staging/raw",
        include_extensions: Optional[List[str]] = None,
    ) -> None:
        if isinstance(source_path, dict):
            # Config object passed
            cfg = source_path
            src = cfg.get("source", {}).get("local_backup_path", ".")
            stg = cfg.get("output_paths", {}).get("staged_raw_dir", "./staging/raw")
            exts = cfg.get("source", {}).get("include_extensions", None)
            
            p_src = Path(src)
            if not p_src.exists():
                candidate = (Path(__file__).resolve().parent.parent / src).resolve()
                if candidate.exists():
                    p_src = candidate
                else:
                    alt = (Path(__file__).resolve().parent.parent.parent / "compression_server").resolve()
                    if alt.exists():
                        p_src = alt
                    else:
                        p_src = p_src.resolve()
            self.source_path = p_src.resolve()

            p_stg = Path(stg)
            if not p_stg.is_absolute():
                p_stg = (Path(__file__).resolve().parent.parent / stg).resolve()
            self.staging_raw_dir = p_stg
            self.include_extensions = set(exts or ALLOWED_EXTENSIONS)
        else:
            p_src = Path(source_path)
            if not p_src.exists():
                p_src = (Path(__file__).resolve().parent.parent.parent / "compression_server").resolve()
            self.source_path = p_src.resolve()
            p_stg = Path(staging_raw_dir)
            if not p_stg.is_absolute():
                p_stg = (Path(__file__).resolve().parent.parent / staging_raw_dir).resolve()
            self.staging_raw_dir = p_stg
            self.include_extensions = set(include_extensions or ALLOWED_EXTENSIONS)
        self.staged_raw_dir = str(self.staging_raw_dir)
        self.manifest_file = self.staging_raw_dir / "fetch_manifest.json"
        self.manifest = self._load_manifest()

    def discover_dates(self) -> List[str]:
        return self.discover_available_dates()

    def fetch_date_files(self, target_date_str: str) -> Dict[str, Any]:
        return self.fetch_files_for_date(target_date_str)

    def _load_manifest(self) -> Dict[str, Any]:
        if self.manifest_file.exists():
            try:
                with open(self.manifest_file, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception:
                pass
        return {"files": {}, "last_sync": None}

    def _save_manifest(self) -> None:
        self.manifest_file.parent.mkdir(parents=True, exist_ok=True)
        self.manifest["last_sync"] = datetime.utcnow().isoformat() + "Z"
        with open(self.manifest_file, "w", encoding="utf-8") as f:
            json.dump(self.manifest, f, indent=2)

    def discover_available_dates(self) -> List[str]:
        """
        Scan source directory to discover all available production dates (sorted chronologically).
        """
        dates: Set[str] = set()
        if not self.source_path.exists():
            logger.warning(f"Source directory '{self.source_path}' does not exist.")
            return []

        # 1. Date folders in source root
        for entry in self.source_path.iterdir():
            if entry.is_dir() and DATE_FOLDER_REGEX.match(entry.name):
                dates.add(entry.name)

        # 2. Recursive scan for dates in file/folder names
        for root, dirs, files in os.walk(self.source_path):
            rel_dir = Path(root).name
            if DATE_FOLDER_REGEX.match(rel_dir):
                dates.add(rel_dir)
            for f in files:
                match = DATE_IN_NAME_REGEX.search(f)
                if match:
                    dates.add(match.group(1))

        sorted_dates = sorted(list(dates))
        logger.info(f"Discovered {len(sorted_dates)} production dates in source: {sorted_dates[:5]} ... {sorted_dates[-2:] if len(sorted_dates) > 2 else ''}")
        return sorted_dates

    def fetch_files_for_date(self, target_date_str: str) -> Dict[str, Any]:
        """
        Copy all relevant reports, databases, and logs for target_date_str into staging/raw/<target_date_str>/.
        Returns dictionary of staged file paths and metadata.
        """
        date_staging_dir = self.staging_raw_dir / target_date_str
        date_staging_dir.mkdir(parents=True, exist_ok=True)
        db_staging_dir = self.staging_raw_dir / "databases"
        db_staging_dir.mkdir(parents=True, exist_ok=True)

        staged_files: List[Dict[str, Any]] = []

        # 1. Look for direct date folder: e.g. <source_path>/2026-09-25/
        source_date_dir = self.source_path / target_date_str
        candidates: List[Path] = []

        if source_date_dir.exists() and source_date_dir.is_dir():
            for f in source_date_dir.iterdir():
                if f.is_file() and f.suffix.lower() in self.include_extensions:
                    candidates.append(f)

        # 2. Also check root directory for shared/root MDB files (SawcData.mdb, Sawc.mdb) and root reports
        for f in self.source_path.iterdir():
            if f.is_file() and f.suffix.lower() in self.include_extensions:
                # Include databases always or if filename matches target date
                if f.suffix.lower() in (".mdb", ".accdb"):
                    # Stage root databases to databases/ folder
                    dest_db = db_staging_dir / f.name
                    self._copy_if_modified(f, dest_db, staged_files, category="database")
                elif target_date_str in f.name:
                    candidates.append(f)

        # Copy candidate files
        for src_file in candidates:
            dest_file = date_staging_dir / src_file.name
            self._copy_if_modified(src_file, dest_file, staged_files, category="report")

        self._save_manifest()
        logger.info(f"Staged {len(staged_files)} files for date {target_date_str} in {date_staging_dir}")
        return {
            "date": target_date_str,
            "staging_dir": str(date_staging_dir),
            "files": staged_files,
        }

    def _copy_if_modified(
        self,
        src: Path,
        dst: Path,
        staged_list: List[Dict[str, Any]],
        category: str,
    ) -> None:
        src_stat = src.stat()
        mtime = src_stat.st_mtime
        size = src_stat.st_size
        cache_key = str(src.resolve())

        cached = self.manifest["files"].get(cache_key)
        needs_copy = True

        if cached and dst.exists():
            if cached.get("size") == size and abs(cached.get("mtime", 0) - mtime) < 1.0:
                needs_copy = False

        if needs_copy:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)
            sha = compute_file_sha256(dst)
            self.manifest["files"][cache_key] = {
                "dest": str(dst.resolve()),
                "size": size,
                "mtime": mtime,
                "sha256": sha,
                "copied_at": datetime.utcnow().isoformat() + "Z",
                "category": category,
            }
            logger.info(f"Copied {src.name} -> {dst.relative_to(self.staging_raw_dir.parent)}")
        else:
            sha = cached.get("sha256", "")

        staged_list.append({
            "name": dst.name,
            "path": str(dst.resolve()),
            "category": category,
            "size": size,
            "sha256": sha,
            "needs_copy": needs_copy,
        })


# Backward compatible alias
FileFetcher = CompressionFileFetcher
