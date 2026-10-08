"""
Staging Reader: Discovers and parses staged JSON and metadata files from disk.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional


class StagingReader:
    def __init__(self, staging_root: str) -> None:
        self.staging_root = Path(staging_root)
        self.api_raw_path = self.staging_root / "api_raw"
        self.metadata_path = self.staging_root / "metadata" / "api"

    def get_available_assets(self) -> List[str]:
        """Return list of asset IDs found in staging."""
        if not self.api_raw_path.exists():
            return []
        assets = []
        for p in self.api_raw_path.iterdir():
            if p.is_dir():
                # E.g. '10094_f7e11a1e' -> '10094'
                asset_id = p.name.split("_")[0]
                assets.append(asset_id)
        return sorted(list(set(assets)))

    def get_asset_dir(self, asset_id: str) -> Optional[Path]:
        if not self.api_raw_path.exists():
            return None
        for p in self.api_raw_path.iterdir():
            if p.is_dir() and p.name.startswith(f"{asset_id}_"):
                return p
            if p.is_dir() and p.name == asset_id:
                return p
        return None

    def read_latest_batch_info(self, asset_id: str) -> List[Dict[str, Any]]:
        asset_dir = self.get_asset_dir(asset_id)
        if not asset_dir:
            return []

        batch_info_files = sorted(asset_dir.glob("batch_info_*.json"), key=os.path.getmtime, reverse=True)
        if not batch_info_files:
            return []

        try:
            with open(batch_info_files[0], "r", encoding="utf-8") as f:
                data = json.load(f)
                return data if isinstance(data, list) else [data]
        except Exception:
            return []

    def read_batch_datasets(self, asset_id: str, batch_no: str, lot_no: str) -> Dict[str, Any]:
        """Read all staged dataset JSONs for a given asset, batch, and lot."""
        asset_dir = self.get_asset_dir(asset_id)
        if not asset_dir:
            return {}

        # Search for matching batch subfolder
        batch_dirs = [d for d in asset_dir.iterdir() if d.is_dir() and (d.name.startswith(f"{batch_no}_") or d.name == batch_no)]
        if not batch_dirs:
            return {}

        batch_dir = batch_dirs[0]

        # Search for matching lot subfolder
        lot_dirs = [d for d in batch_dir.iterdir() if d.is_dir() and (d.name.startswith(f"{lot_no}_") or d.name == lot_no or lot_no == "NA")]
        if not lot_dirs:
            # If lot_no is 'NA', take any lot dir
            lot_dirs = [d for d in batch_dir.iterdir() if d.is_dir()]
            if not lot_dirs:
                return {}

        lot_dir = lot_dirs[0]
        results: Dict[str, Any] = {}

        for json_file in lot_dir.glob("*.json"):
            dataset_name = json_file.stem.split("_")[0]
            # Handle multi-part names e.g. batch_summary, rmg_operational_data
            name_parts = json_file.stem.split("_")
            # Find base dataset name by looking for timestamp pattern or known prefixes
            full_stem = json_file.stem
            for known in [
                "batch_summary", "machine_summary", "login_logout", "users",
                "alarms", "audit_trail", "rmg_operational_data", "rmg_recipe",
                "fbd_operational_data", "fbd_recipe", "fbd_min_max",
                "coat_operational_data", "coat_recipe", "coat_min_max",
                "blend_operational_data", "blend_recipe"
            ]:
                if full_stem.startswith(known):
                    dataset_name = known
                    break

            try:
                with open(json_file, "r", encoding="utf-8") as f:
                    results[dataset_name] = json.load(f)
            except Exception:
                continue

        return results
