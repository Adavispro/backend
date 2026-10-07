"""Fetch documented datasets and retain original responses with staging metadata."""

import hashlib
import json
import re
from datetime import datetime, timezone

from api.api_client import APIClient
from api.api_parser import batch_sort_date, build_point, parse_batches
from common import atomic_write, project_path, read_json, utc_now, write_json


def safe_part(value):
    source = str(value)
    readable = re.sub(r"[^A-Za-z0-9._-]+", "_", source).strip("._")[:60] or "empty"
    return readable + "_" + hashlib.sha256(source.encode("utf-8")).hexdigest()[:8]


def stamp():
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")


class APIFetcher:
    def __init__(self, config, logger):
        self.config = config
        self.logger = logger
        self.client = APIClient(config)
        self.raw_root = project_path(config["raw_response_folder"])
        self.meta_root = project_path(config["metadata_folder"])

    def _manifest_path(self, asset_id, batch):
        lot = batch["lot_no"] or "NO_LOT"
        return self.meta_root / "api" / safe_part(asset_id) / safe_part(batch["batch_no"]) / safe_part(lot) / "manifest.json"

    def _raw_folder(self, asset_id, batch):
        lot = batch["lot_no"] or "NO_LOT"
        return self.raw_root / safe_part(asset_id) / safe_part(batch["batch_no"]) / safe_part(lot)

    def _datasets(self, asset):
        return self.config["common_batch_datasets"] + asset["datasets"]

    def _needs_fetch(self, batch, manifest, datasets):
        if not self.config.get("deduplicate", True):
            return True
        if manifest.get("batch_status") == "LIVE" and batch["status"] == "COMPLETED":
            return True
        if batch["status"] == "LIVE" and self.config.get("process_live_batches", True):
            return True
        return any(manifest.get("datasets", {}).get(item["name"], {}).get("status") != "SUCCESS" for item in datasets)

    def _fetch_asset(self, asset):
        asset_id = str(asset["asset_id"])
        point = build_point(self.config["batch_list_point"], asset_id)
        raw = self.client.get_dataset(point)
        payload = json.loads(raw)
        batches = parse_batches(payload)
        self.logger.info("asset=%s batches_found=%d", asset_id, len(batches))
        listing_path = self.raw_root / safe_part(asset_id) / f"batch_info_{stamp()}.json"
        if self.config.get("save_raw_data", True):
            atomic_write(listing_path, raw)
        write_json(self.meta_root / "api" / safe_part(asset_id) / "latest_listing.json", {
            "source": "API", "asset_id": asset_id, "source_api": "db:HMI.Batch_Info",
            "source_file": str(listing_path) if self.config.get("save_raw_data", True) else None,
            "fetch_timestamp": utc_now(), "status": "SUCCESS",
            "checksum": hashlib.sha256(raw).hexdigest(), "batches_found": len(batches),
        })
        datasets = self._datasets(asset)
        pending = []
        for batch in batches:
            if batch["status"] == "LIVE" and not self.config.get("process_live_batches", True):
                continue
            path = self._manifest_path(asset_id, batch)
            manifest = read_json(path, {})
            if self._needs_fetch(batch, manifest, datasets):
                pending.append((batch, manifest, path))
            elif self.config.get("log_skipped_records", True):
                self.logger.info("asset=%s batch=%s lot=%s reason=ALREADY_STAGED timestamp=%s",
                                 asset_id, batch["batch_no"], batch["lot_no"], utc_now())
        pending.sort(key=lambda item: batch_sort_date(item[0]), reverse=True)
        pending.sort(key=lambda item: (bool(item[1].get("last_selected_at")), item[1].get("last_selected_at") or ""))
        limit = max(0, int(self.config["max_batches_per_asset_per_cycle"]))
        selected = pending[:limit]
        self.logger.info("asset=%s batches_selected=%d pending=%d", asset_id, len(selected), len(pending))
        for batch, manifest, path in selected:
            self._fetch_batch(asset, batch, datasets, manifest, path)

    def _fetch_batch(self, asset, batch, datasets, manifest, path):
        asset_id = str(asset["asset_id"])
        became_completed = manifest.get("batch_status") == "LIVE" and batch["status"] == "COMPLETED"
        manifest.update({
            "source": "API", "asset_id": asset_id, "batch_id": batch["batch_no"],
            "lot_number": batch["lot_no"], "product_no": batch["product_no"],
            "source_timestamp": batch["batch_start_date"],
            "batch_end_date": batch["batch_end_date"], "batch_status": batch["status"],
            "last_selected_at": utc_now(),
        })
        manifest.setdefault("datasets", {})
        write_json(path, manifest)
        for item in datasets:
            name = item["name"]
            previous = manifest["datasets"].get(name, {})
            if (self.config.get("deduplicate", True) and not became_completed and batch["status"] == "COMPLETED"
                    and previous.get("status") == "SUCCESS"):
                continue
            try:
                point = build_point(item["point"], asset_id, batch["batch_no"], batch["lot_no"])
                raw = self.client.get_dataset(point)
                json.loads(raw)
                output = self._raw_folder(asset_id, batch) / f"{safe_part(name)}_{stamp()}.json"
                if self.config.get("save_raw_data", True):
                    atomic_write(output, raw)
                manifest["datasets"][name] = {
                    "source_api": point,
                    "source_file": str(output) if self.config.get("save_raw_data", True) else None,
                    "fetch_timestamp": utc_now(), "status": "SUCCESS",
                    "checksum": hashlib.sha256(raw).hexdigest(),
                }
                self.logger.info("asset=%s batch=%s lot=%s dataset=%s status=STAGED",
                                 asset_id, batch["batch_no"], batch["lot_no"], name)
            except (RuntimeError, ValueError, OSError) as error:
                manifest["datasets"][name] = {
                    "source_api": item["point"].split("<", 1)[0],
                    "fetch_timestamp": utc_now(), "status": "FAILED", "error_message": str(error),
                }
                self.logger.error("asset=%s batch=%s lot=%s dataset=%s status=FAILED error=%s",
                                  asset_id, batch["batch_no"], batch["lot_no"], name, error)
            write_json(path, manifest)

    def run_cycle(self):
        self.logger.info("API cycle start")
        if not self.config.get("enabled", True):
            self.logger.info("API scheduler disabled")
            return
        for asset in self.config["assets"]:
            if not asset.get("enabled", True):
                continue
            try:
                self._fetch_asset(asset)
            except (RuntimeError, ValueError, OSError, json.JSONDecodeError) as error:
                self.logger.error("asset=%s status=FAILED error=%s", asset["asset_id"], error)
        self.logger.info("API cycle end")
