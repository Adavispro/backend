"""Copy original Compression files with source metadata and version history."""

import hashlib
import os
import shutil
import time
from datetime import datetime, timezone
from pathlib import Path

from common import project_path, read_json, utc_now, write_json
from file.network_file_reader import discover_files


def checksum(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def file_stamp():
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")


class FileFetcher:
    def __init__(self, config, logger):
        self.config = config
        self.logger = logger
        self.source_root = Path(config["network_path"])
        self.dest_root = project_path(config["local_staging_folder"])
        self.index_path = project_path(config["metadata_folder"]) / "files" / "file_index.json"

    def _copy_with_retry(self, source, relative):
        attempts = max(1, int(self.config.get("retry_count", 3)))
        delay = max(0, float(self.config.get("retry_delay_seconds", 5)))
        last_error = None
        for attempt in range(attempts):
            temp = None
            try:
                before = source.stat()
                target_folder = self.dest_root / relative.parent
                target_folder.mkdir(parents=True, exist_ok=True)
                temp = target_folder / (".copy-" + file_stamp() + "-" + source.name)
                shutil.copy2(source, temp)
                after = source.stat()
                if (before.st_mtime_ns, before.st_size) != (after.st_mtime_ns, after.st_size):
                    raise OSError("Source file changed during copy")
                digest = checksum(temp)
                destination = target_folder / f"{source.stem}__{file_stamp()}__{digest[:8]}{source.suffix}"
                os.replace(temp, destination)
                return destination, digest, after
            except OSError as error:
                last_error = error
                if temp is not None:
                    temp.unlink(missing_ok=True)
                if attempt + 1 < attempts:
                    time.sleep(delay)
        raise last_error

    def run_cycle(self):
        self.logger.info("File cycle start")
        if not self.config.get("enabled", True):
            self.logger.info("File scheduler disabled")
            return
        if self.config["network_path"].startswith("CONFIGURE_"):
            self.logger.error("Set file_fetch.network_path before running the Compression scheduler")
            return
        try:
            files = discover_files(self.source_root, self.config["folder_grouping"], self.config["daily_folder_format"])
        except (OSError, ValueError) as error:
            self.logger.error("File discovery failed: %s", error)
            return
        self.logger.info("files_discovered=%d", len(files))
        index = read_json(self.index_path, {})
        pending = []
        for source in files:
            try:
                stat = source.stat()
            except OSError as error:
                self.logger.error("source=%s status=FAILED error=%s", source, error)
                continue
            key = str(source.resolve())
            previous = index.get(key, {})
            if (self.config.get("deduplicate", True) and previous.get("status") == "SUCCESS"
                    and previous.get("source_mtime_ns") == stat.st_mtime_ns
                    and previous.get("source_size") == stat.st_size):
                self.logger.info("source=%s reason=ALREADY_STAGED", source)
                continue
            pending.append((source, stat))
        pending.sort(key=lambda item: item[1].st_mtime_ns, reverse=True)
        limit = max(0, int(self.config.get("max_files_per_cycle", 100)))
        self.logger.info("files_selected=%d pending=%d", min(limit, len(pending)), len(pending))
        for source, _ in pending[:limit]:
            key = str(source.resolve())
            relative = source.relative_to(self.source_root)
            try:
                destination, digest, stat = self._copy_with_retry(source, relative)
                record = {
                    "source": "COMPRESSION_FILE", "source_file": str(source),
                    "source_filename": source.name, "source_relative_path": str(relative),
                    "source_mtime_ns": stat.st_mtime_ns, "source_size": stat.st_size,
                    "file_modified_timestamp": datetime.fromtimestamp(stat.st_mtime, timezone.utc).isoformat(),
                    "fetch_timestamp": utc_now(), "staged_file": str(destination),
                    "checksum": digest, "status": "SUCCESS",
                }
                versions = index.get(key, {}).get("versions", [])
                versions.append({
                    "fetch_timestamp": record["fetch_timestamp"],
                    "file_modified_timestamp": record["file_modified_timestamp"],
                    "staged_file": record["staged_file"], "checksum": digest,
                })
                record["versions"] = versions
                index[key] = record
                self.logger.info("source=%s staged_file=%s status=STAGED", source, destination)
            except OSError as error:
                index[key] = {
                    "source": "COMPRESSION_FILE", "source_file": str(source),
                    "source_filename": source.name, "source_relative_path": str(relative),
                    "fetch_timestamp": utc_now(), "status": "FAILED", "error_message": str(error),
                    "versions": index.get(key, {}).get("versions", []),
                }
                self.logger.error("source=%s status=FAILED error=%s", source, error)
            write_json(self.index_path, index)
        self.logger.info("File cycle end")
