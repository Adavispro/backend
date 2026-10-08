"""
Mock Plant API Server.
Serves the real 4 Equipments sample JSON datasets locally,
mimicking /fwxapi/rest/v1/Dataset?pointName=...
Zero changes needed when moving from mock testing to production.
"""

from __future__ import annotations

import json
import logging
import os
import re
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from threading import Thread
from typing import Any, Dict, Optional

logger = logging.getLogger("api_ingestion_service.mock_server")


def extract_point_params(point_name: str) -> Dict[str, str]:
    """Parse pointName string like:
    db:HMI.Batch_Info<@AssetId=10094, @Batch_No=''>
    db:HMI.RMG_op_data<@AssetId='10094', @BatchNo='ACY0026016', @LotNo='01'>
    """
    params: Dict[str, str] = {}
    
    # Extract dataset identifier
    match_ds = re.search(r"HMI\.([A-Za-z0-9_]+)", point_name)
    if match_ds:
        params["dataset"] = match_ds.group(1).lower()

    # Extract AssetId
    match_asset = re.search(r"@AssetId=['\"]?([0-9]+)['\"]?", point_name, re.IGNORECASE)
    if match_asset:
        params["asset_id"] = match_asset.group(1)

    # Extract BatchNo
    match_batch = re.search(r"@Batch_?No=['\"]?([^,'\">]*)['\"]?", point_name, re.IGNORECASE)
    if match_batch:
        params["batch_no"] = match_batch.group(1).strip()

    # Extract LotNo
    match_lot = re.search(r"@Lot_?No=['\"]?([^,'\">]*)['\"]?", point_name, re.IGNORECASE)
    if match_lot:
        params["lot_no"] = match_lot.group(1).strip()

    return params


class SampleDataStore:
    def __init__(self, staging_path: Path) -> None:
        self.staging_path = staging_path
        self.raw_api_path = self.staging_path / "api_raw"

    def find_batch_info_json(self, asset_id: str) -> Optional[list]:
        for folder in self.raw_api_path.glob(f"{asset_id}_*"):
            batch_info_files = sorted(folder.glob("batch_info_*.json"), key=os.path.getmtime, reverse=True)
            if batch_info_files:
                with open(batch_info_files[0], "r", encoding="utf-8") as f:
                    return json.load(f)
        return []

    def find_dataset_json(self, asset_id: str, dataset_key: str, batch_no: str, lot_no: str) -> Optional[list]:
        for asset_folder in self.raw_api_path.glob(f"{asset_id}_*"):
            # Look for matching batch folder
            batch_folders = [d for d in asset_folder.iterdir() if d.is_dir() and (d.name.startswith(f"{batch_no}_") or d.name == batch_no)]
            if not batch_folders:
                # Fallback to any batch folder for demo testing if exact batch folder not found
                batch_folders = [d for d in asset_folder.iterdir() if d.is_dir()]

            for b_dir in batch_folders:
                lot_folders = [d for d in b_dir.iterdir() if d.is_dir()]
                for l_dir in lot_folders:
                    for jf in l_dir.glob("*.json"):
                        fname = jf.stem.lower()
                        # Map common dataset keys
                        key_aliases = {
                            "batch_summary": ["batch_summary"],
                            "mach_summary": ["machine_summary"],
                            "machine_summary": ["machine_summary"],
                            "login": ["login_logout"],
                            "login_logout": ["login_logout"],
                            "users": ["users"],
                            "alarms": ["alarms"],
                            "audit_trail": ["audit_trail"],
                            "rmg_op_data": ["rmg_operational_data"],
                            "rmg_recipe": ["rmg_recipe"],
                            "fbd_op_data": ["fbd_operational_data"],
                            "fbd_recipe": ["fbd_recipe"],
                            "fbd_min_max": ["fbd_min_max"],
                            "coat_op_data": ["coat_operational_data"],
                            "coat_recipe": ["coat_recipe"],
                            "coat_min_max": ["coat_min_max"],
                            "blend_op_data": ["blend_operational_data"],
                            "blend_recipe": ["blend_recipe"],
                        }
                        aliases = key_aliases.get(dataset_key, [dataset_key])
                        if any(fname.startswith(a) for a in aliases):
                            with open(jf, "r", encoding="utf-8") as f:
                                return json.load(f)
        return []


def create_request_handler(data_store: SampleDataStore):
    class MockAPIRequestHandler(BaseHTTPRequestHandler):
        def log_message(self, format, *args):
            logger.debug("%s - - [%s] %s" % (self.address_string(), self.log_date_time_string(), format % args))

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            query_params = urllib.parse.parse_qs(parsed.query)

            if parsed.path.endswith("/fwxapi/rest/v1/Dataset") or "pointName" in query_params:
                point_name = query_params.get("pointName", [""])[0]
                params = extract_point_params(point_name)
                asset_id = params.get("asset_id", "10094")
                dataset_name = params.get("dataset", "")
                batch_no = params.get("batch_no", "")
                lot_no = params.get("lot_no", "")

                if dataset_name == "batch_info" or "batch_info" in point_name.lower():
                    data = data_store.find_batch_info_json(asset_id)
                else:
                    data = data_store.find_dataset_json(asset_id, dataset_name, batch_no, lot_no)

                response_body = json.dumps(data if data is not None else []).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(response_body)))
                self.end_headers()
                self.wfile.write(response_body)
                return

            # Healthcheck
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            resp = json.dumps({"status": "running", "mock": "plant_api"}).encode("utf-8")
            self.send_header("Content-Length", str(len(resp)))
            self.end_headers()
            self.wfile.write(resp)

    return MockAPIRequestHandler


class MockServerThread:
    def __init__(self, host: str = "127.0.0.1", port: int = 8001, staging_path: Optional[str] = None) -> None:
        self.host = host
        self.port = port
        if staging_path is None:
            module_dir = Path(__file__).resolve().parent.parent
            local_samples = module_dir / "sample_data"
            if local_samples.exists():
                staging_path = local_samples
            else:
                base_dir = module_dir.parent.parent
                staging_path = base_dir / "sample_data" / "Sample Data - 071026 2139" / "4 Equipments Data" / "staging"
        self.staging_path = Path(staging_path)
        self.data_store = SampleDataStore(self.staging_path)
        self.server: Optional[HTTPServer] = None
        self.thread: Optional[Thread] = None

    def start(self) -> None:
        handler = create_request_handler(self.data_store)
        self.server = HTTPServer((self.host, self.port), handler)
        self.thread = Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        logger.info(f"Mock API Server started at http://{self.host}:{self.port}")

    def stop(self) -> None:
        if self.server:
            self.server.shutdown()
            self.server.server_close()
            logger.info("Mock API Server stopped")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    server = MockServerThread()
    server.start()
    print(f"Mock Server running on http://127.0.0.1:8001. Press Ctrl+C to stop.")
    try:
        import time
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        server.stop()
