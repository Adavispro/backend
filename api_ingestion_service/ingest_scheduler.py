"""
API Ingestion Scheduler.
Fetches raw batch, recipe, operational, alarm, and audit data from Plant API (or local Mock Service),
cleans payload via the cleaning engine, and ingests into MongoDB with multi-stage batch tracking.
"""

from __future__ import annotations

import argparse
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import logging
from logging.handlers import RotatingFileHandler
import os
import signal
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import quote

import requests
import urllib3
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# Ensure module and parent directory resolution for standalone portability
base_dir = Path(__file__).resolve().parent
if str(base_dir) not in sys.path:
    sys.path.insert(0, str(base_dir))
if str(base_dir.parent) not in sys.path:
    sys.path.insert(0, str(base_dir.parent))

try:
    from api_ingestion_service.core.auth_manager import OAuth2TokenManager
    from api_ingestion_service.core.cleaner import clean_batch_info_row, clean_record_list, strip_tags
    from api_ingestion_service.core.mongo_loader import MongoIngestionLoader
    from api_ingestion_service.mock_service.sample_data_server import MockServerThread
except ImportError:
    from core.auth_manager import OAuth2TokenManager
    from core.cleaner import clean_batch_info_row, clean_record_list, strip_tags
    from core.mongo_loader import MongoIngestionLoader
    from mock_service.sample_data_server import MockServerThread

# Configure Logging (Console + Rotating File Log)
log_dir = base_dir / "logs"
log_dir.mkdir(parents=True, exist_ok=True)
log_file = log_dir / "ingestion.log"

logger = logging.getLogger("api_ingestion_service")
logger.setLevel(logging.INFO)

# Console handler
console_handler = logging.StreamHandler(sys.stdout)
console_handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s"))
logger.addHandler(console_handler)

# File handler (10 MB max, up to 5 backups)
file_handler = RotatingFileHandler(log_file, maxBytes=10 * 1024 * 1024, backupCount=5, encoding="utf-8")
file_handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s"))
logger.addHandler(file_handler)


class PlantAPIClient:
    def __init__(
        self,
        base_url: str,
        token_manager: OAuth2TokenManager,
        dataset_path: str = "/fwxapi/rest/v1/Dataset",
        verify_tls: bool = False,
        timeout: int = 60,
        max_retries: int = 3,
        backoff_factor: float = 0.5,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.dataset_path = dataset_path
        self.token_manager = token_manager
        self.verify_tls = verify_tls
        self.timeout = timeout

        if not self.verify_tls:
            urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

        self.session = requests.Session()
        retry_strategy = Retry(
            total=max_retries,
            backoff_factor=backoff_factor,
            status_forcelist=[500, 502, 503, 504],
            allowed_methods=["GET"],
            raise_on_status=False,
        )
        adapter = HTTPAdapter(pool_connections=20, pool_maxsize=50, max_retries=retry_strategy)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)

    def fetch_point(self, point_name: str) -> List[Dict[str, Any]]:
        url = f"{self.base_url}{self.dataset_path}?pointName={quote(point_name, safe=':@=<>,\'')}"

        def _do_request(token: Optional[str]) -> requests.Response:
            headers = {
                "Accept": "application/json",
                "User-Agent": "ADAVIS-Ingestion-Scheduler/1.0",
            }
            if token:
                headers["Authorization"] = f"Bearer {token}"
            return self.session.get(url, headers=headers, timeout=self.timeout, verify=self.verify_tls)

        try:
            token = self.token_manager.get_token()
            resp = _do_request(token)

            # Handle 401 Unauthorized by auto-refreshing token and retrying once
            if resp.status_code == 401 and self.token_manager.auth_type.startswith("oauth2"):
                logger.warning(f"Received 401 Unauthorized. Refreshing OAuth 2.0 token and retrying...")
                token = self.token_manager.get_token(force_refresh=True)
                resp = _do_request(token)

            if resp.status_code == 200:
                data = resp.json()
                return data if isinstance(data, list) else [data]

            logger.warning(f"API request failed with status {resp.status_code} for point: {point_name}")
            return []
        except Exception as exc:
            logger.error(f"Error requesting point {point_name}: {exc}")
            return []


def load_env_file(env_path: Optional[Path] = None) -> None:
    """Zero-dependency .env loader."""
    if env_path is None:
        env_path = base_dir / ".env"
    if not env_path.exists():
        env_path = base_dir.parent / ".env"
    if not env_path.exists():
        return
    try:
        with open(env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, val = line.split("=", 1)
                key = key.strip()
                val = val.strip().strip("\"'")
                if key and key not in os.environ:
                    os.environ[key] = val
    except Exception:
        pass


class IngestionSchedulerService:
    def __init__(
        self,
        config_path: Optional[str] = None,
        use_mock: Optional[bool] = None,
        ingestion_mode: Optional[str] = None,
        mock_port: Optional[int] = None,
    ) -> None:
        load_env_file()

        if config_path is None:
            config_path = str(base_dir / "config" / "ingestion_config.json")

        with open(config_path, "r", encoding="utf-8") as f:
            self.config = json.load(f)

        # Ingestion Mode & Backup Configuration
        ingest_cfg = self.config.get("ingestion", {})
        if ingestion_mode:
            self.ingestion_mode = ingestion_mode.strip().upper()
        else:
            self.ingestion_mode = os.getenv("INGESTION_MODE", ingest_cfg.get("ingestion_mode", "APPEND")).upper()

        # Environment / CLI variable overrides for mock
        if use_mock is not None:
            self.use_mock = use_mock
        else:
            env_use_mock = os.getenv("USE_MOCK_API")
            if env_use_mock is not None:
                self.use_mock = env_use_mock.lower() in ("true", "1", "yes")
            else:
                self.use_mock = self.config["api"].get("use_mock", False)

        self.mock_server: Optional[MockServerThread] = None
        auth_cfg = self.config.get("auth", {})
        api_cfg = self.config.get("api", {})

        if self.use_mock:
            mock_cfg = self.config.get("mock_server", {})
            mock_host = os.getenv("MOCK_SERVER_HOST", mock_cfg.get("host", "127.0.0.1"))
            resolved_port = mock_port or int(os.getenv("MOCK_SERVER_PORT", mock_cfg.get("port", 8001)))
            sample_data_rel = mock_cfg.get("sample_data_path", "./sample_data")
            sample_data_abs = (base_dir / sample_data_rel).resolve()
            self.mock_server = MockServerThread(host=mock_host, port=resolved_port, staging_path=str(sample_data_abs))
            self.mock_server.start()
            api_base_url = f"http://{mock_host}:{resolved_port}"
            verify_tls = False
            token_mgr = OAuth2TokenManager(auth_type="none")
            logger.info(f"Using Mock Server at {api_base_url}")
        else:
            api_base_url = os.getenv("PLANT_API_BASE_URL", api_cfg.get("live_base_url", "https://u3-miebmr-srv-t"))
            verify_tls_env = os.getenv("PLANT_API_VERIFY_TLS")
            verify_tls = verify_tls_env.lower() in ("true", "1") if verify_tls_env is not None else api_cfg.get("verify_tls", False)

            # Determine authentication mode: "oauth2" | "static_token" | "none"
            auth_type = os.getenv("AUTH_TYPE", auth_cfg.get("auth_type", "static_token")).lower()
            static_token = os.getenv("PLANT_API_BEARER_TOKEN", api_cfg.get("bearer_token", ""))

            token_mgr = OAuth2TokenManager(
                auth_type=auth_type,
                token_url=os.getenv("OAUTH2_TOKEN_URL", auth_cfg.get("token_url", "https://u3-miebmr-srv-t/fwxserverweb/security/connect/token")),
                auth_url=os.getenv("OAUTH2_AUTH_URL", auth_cfg.get("auth_url", "https://u3-miebmr-srv-t/fwxserverweb/security/connect/authorize")),
                callback_url=os.getenv("OAUTH2_CALLBACK_URL", auth_cfg.get("callback_url", "http://u3-miebmr-srv-t")),
                client_id=os.getenv("OAUTH2_CLIENT_ID", auth_cfg.get("client_id", "In_house_client")),
                client_secret=os.getenv("OAUTH2_CLIENT_SECRET", auth_cfg.get("client_secret", "")),
                username=os.getenv("OAUTH2_USERNAME", auth_cfg.get("username", "")),
                password=os.getenv("OAUTH2_PASSWORD", auth_cfg.get("password", "")),
                static_token=static_token,
                verify_tls=verify_tls,
                timeout=int(os.getenv("OAUTH2_TIMEOUT", "30")),
            )
            logger.info(f"Using Live Plant API at {api_base_url} (Auth Mode: {auth_type.upper()})")

        self.api_client = PlantAPIClient(
            base_url=api_base_url,
            token_manager=token_mgr,
            dataset_path=self.config["api"].get("dataset_path", "/fwxapi/rest/v1/Dataset"),
            verify_tls=verify_tls,
            timeout=int(os.getenv("PLANT_API_TIMEOUT", self.config["api"].get("request_timeout_seconds", 60))),
            max_retries=int(os.getenv("PLANT_API_MAX_RETRIES", self.config["api"].get("max_retries", 3))),
            backoff_factor=float(os.getenv("PLANT_API_BACKOFF", self.config["api"].get("backoff_factor", 0.5))),
        )

        db_cfg = self.config.get("database", {})
        mongo_uri = os.getenv("MONGO_URI") or os.getenv("MONGODB_URI") or db_cfg.get("mongo_uri")
        db_name = os.getenv("MONGO_DB_NAME") or os.getenv("MONGODB_DATABASE") or db_cfg.get("db_name", "adavis_platform")

        self.loader = MongoIngestionLoader(
            mongo_uri=mongo_uri,
            db_name=db_name,
        )

        backup_env = os.getenv("BACKUP_BEFORE_TRUNCATE")
        self.backup_before_truncate = backup_env.lower() in ("true", "1", "yes") if backup_env is not None else ingest_cfg.get("backup_before_truncate", True)

        self.asset_codes = [a.get("code") or a["asset_id"] for a in self.config.get("assets", [])]
        self.loader.prepare_database_for_ingestion(
            asset_codes=self.asset_codes,
            ingestion_mode=self.ingestion_mode,
            backup_enabled=self.backup_before_truncate,
        )
        self.running = True
        self._stop_event = threading.Event()
        self.last_results: Optional[Dict[str, Any]] = None
        self.cycle_count: int = 0
        self.http_server: Optional[HTTPServer] = None
        self.http_thread: Optional[threading.Thread] = None

    def process_asset(self, asset: Dict[str, Any]) -> Dict[str, Any]:
        asset_id = asset["asset_id"]
        equipment_code = asset.get("code") or asset_id
        asset_name = asset["name"]
        equipment_type = asset.get("equipment_type", "RMG")
        stage_order = asset.get("stage_order", 1)
        stage_name = asset.get("stage_name", "Stage")
        datasets_cfg = asset.get("datasets", {})

        job_run_id = self.loader.start_job_run(equipment_code)
        logger.info(f"Running ingestion cycle for {asset_name} (Code: {equipment_code}, ID: {asset_id})")

        # 1. Fetch Batch_Info
        batch_info_point = datasets_cfg.get("batch_info", f"db:HMI.Batch_Info<@AssetId={asset_id}, @Batch_No=''>")
        raw_batch_info = self.api_client.fetch_point(batch_info_point)

        # Batch processing limit: Asset-level override -> Env Variable -> Global config -> default (5)
        global_max = int(os.getenv("MAX_BATCHES_PER_ASSET_PER_CYCLE", os.getenv("MAX_BATCHES_PER_DEVICE", self.config["ingestion"].get("max_batches_per_asset_per_cycle", 5))))
        max_batches = int(asset.get("max_batches_per_cycle", asset.get("max_batches", global_max)))
        batches_processed = 0

        for raw_b in raw_batch_info[:max_batches]:
            cleaned_b = clean_batch_info_row(raw_b)
            batch_no = cleaned_b["batch_no"]
            lot_no = cleaned_b["lot_no"]
            product_code = cleaned_b["product_code"]
            product_name = cleaned_b["product_name"]

            if not batch_no:
                continue

            batches_processed += 1
            # Upsert product catalog
            self.loader.upsert_product(product_code, product_name)

            # 2. Fetch Machine Summary to resolve dynamic Equipment ID
            current_equipment_code = equipment_code
            mach_point_template = datasets_cfg.get("machine_summary")
            if mach_point_template:
                point = mach_point_template.format(asset_id=asset_id, batch_no=batch_no, lot_no=lot_no)
                raw_mach = self.api_client.fetch_point(point)
                for m_row in clean_record_list(raw_mach):
                    eq_id = m_row.get("Equipment ID") or m_row.get("equipment_id") or m_row.get("Equipment_ID") or m_row.get("EquipmentId")
                    if eq_id:
                        current_equipment_code = str(eq_id).strip()
                        break

            # 3. Fetch Users to identify supervisor and operator
            supervisor_name = ""
            operator_name = ""
            cleaned_users: List[Dict[str, Any]] = []
            user_point_template = datasets_cfg.get("users") or datasets_cfg.get("login_logout")
            if user_point_template:
                point = user_point_template.format(asset_id=asset_id, batch_no=batch_no, lot_no=lot_no)
                raw_users = self.api_client.fetch_point(point)
                cleaned_users = clean_record_list(raw_users)
                for u in cleaned_users:
                    uname = str(u.get("User Name") or u.get("user_name") or "")
                    if not supervisor_name and "supervisor" in uname.lower():
                        supervisor_name = uname
                    if not operator_name and "operator" in uname.lower():
                        operator_name = uname

            # 4. Fetch Audit Trail
            audit_records: List[Dict[str, Any]] = []
            audit_point_template = datasets_cfg.get("audit_trail")
            if audit_point_template:
                point = audit_point_template.format(asset_id=asset_id, batch_no=batch_no, lot_no=lot_no)
                raw_audits = self.api_client.fetch_point(point)
                audit_records = clean_record_list(raw_audits)
                self.loader.sync_audits(
                    asset_code=current_equipment_code,
                    batch_no=batch_no,
                    lot_no=lot_no,
                    records=audit_records,
                )
                if not operator_name:
                    for a in audit_records:
                        uname = str(a.get("User Name") or a.get("user_name") or "")
                        if "operator" in uname.lower():
                            operator_name = uname
                            break

            # 5. Fetch Operational Telemetry
            op_event_docs: List[Dict[str, Any]] = []
            op_point_template = datasets_cfg.get("operational_data")
            if op_point_template:
                point = op_point_template.format(asset_id=asset_id, batch_no=batch_no, lot_no=lot_no)
                raw_op = self.api_client.fetch_point(point)
                op_records = clean_record_list(raw_op)
                op_event_docs = self.loader.sync_operational_events(
                    asset_code=current_equipment_code,
                    equipment_type=equipment_type,
                    batch_no=batch_no,
                    lot_no=lot_no,
                    records=op_records,
                    default_status=cleaned_b.get("status") or "RUNNING",
                    default_operator=operator_name,
                    users_records=cleaned_users,
                    audit_records=audit_records,
                )

            # 6. Fetch Alarms
            alarm_point_template = datasets_cfg.get("alarms")
            if alarm_point_template:
                point = alarm_point_template.format(asset_id=asset_id, batch_no=batch_no, lot_no=lot_no)
                raw_alarms = self.api_client.fetch_point(point)
                alarm_records = clean_record_list(raw_alarms)
                self.loader.sync_alarms(asset_code=current_equipment_code, records=alarm_records)

            # 7. Fetch Recipe
            recipe_point_template = datasets_cfg.get("recipe")
            if recipe_point_template:
                point = recipe_point_template.format(asset_id=asset_id, batch_no=batch_no, lot_no=lot_no)
                raw_recipe = self.api_client.fetch_point(point)
                if raw_recipe:
                    self.loader.upsert_recipe(
                        asset_code=current_equipment_code,
                        batch_no=batch_no,
                        lot_no=lot_no,
                        recipe_records=raw_recipe,
                    )

            # 8. Multi-Stage Batch Summary Rollup
            self.loader.upsert_batch_summary(
                batch_no=batch_no,
                lot_no=lot_no,
                product_name=product_name,
                product_code=product_code,
                asset_code=current_equipment_code,
                stage_order=stage_order,
                stage_name=stage_name,
                equipment_type=equipment_type,
                op_event_docs=op_event_docs,
                stage_status=cleaned_b["status"],
                operator_name=operator_name,
                supervisor_name=supervisor_name,
            )

        self.loader.finish_job_run(job_run_id, status="SUCCESS", processed_batches=batches_processed)
        return {"asset_id": asset_id, "equipment_code": equipment_code, "processed_batches": batches_processed, "status": "SUCCESS"}

    def run_cycle(self) -> Dict[str, Any]:
        start_time = datetime.now(timezone.utc)
        enabled_assets = [a for a in self.config.get("assets", []) if a.get("enabled", True)]
        max_workers = min(len(enabled_assets), self.config["ingestion"].get("max_parallel_assets", 4))

        results = []
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            future_map = {executor.submit(self.process_asset, asset): asset for asset in enabled_assets}
            for future in as_completed(future_map):
                try:
                    res = future.result()
                    results.append(res)
                except Exception as exc:
                    asset = future_map[future]
                    logger.error(f"Asset {asset['name']} failed: {exc}")
                    results.append({"asset_id": asset["asset_id"], "status": "FAILED", "error": str(exc)})

        duration = (datetime.now(timezone.utc) - start_time).total_seconds()
        logger.info(f"Ingestion cycle finished in {duration:.2f}s. Results: {results}")
        cycle_result = {"cycle_time": start_time.isoformat(), "duration_seconds": duration, "assets": results}
        self.last_results = cycle_result
        self.cycle_count += 1
        return cycle_result

    def start_http_server(self, host: str = "0.0.0.0", port: int = 8000) -> None:
        """Start a lightweight HTTP control server for standalone service monitoring."""
        service_ref = self

        class IngestionControlHandler(BaseHTTPRequestHandler):
            def log_message(self, format, *args):
                logger.debug("%s - [%s] %s" % (self.address_string(), self.log_date_time_string(), format % args))

            def _send_json(self, status_code: int, data: Dict[str, Any]) -> None:
                body = json.dumps(data, default=str).encode("utf-8")
                self.send_response(status_code)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self) -> None:
                path = self.path.split("?")[0].rstrip("/")
                if path in ("", "/health", "/healthz"):
                    self._send_json(200, {
                        "status": "UP",
                        "service": "api_ingestion_service",
                        "timestamp": datetime.now(timezone.utc).isoformat(),
                        "mode": service_ref.ingestion_mode,
                        "use_mock": service_ref.use_mock,
                    })
                elif path in ("/status", "/metrics"):
                    self._send_json(200, {
                        "status": "RUNNING" if service_ref.running else "STOPPED",
                        "service": "api_ingestion_service",
                        "cycle_count": service_ref.cycle_count,
                        "assets_configured": len(service_ref.config.get("assets", [])),
                        "last_cycle": service_ref.last_results,
                    })
                else:
                    self._send_json(404, {"error": "Not Found", "available_endpoints": ["/health", "/status", "/trigger"]})

            def do_POST(self) -> None:
                path = self.path.split("?")[0].rstrip("/")
                if path in ("/trigger", "/run"):
                    logger.info("Manual ingestion cycle triggered via HTTP POST /trigger")
                    res = service_ref.run_cycle()
                    self._send_json(200, {"status": "SUCCESS", "message": "Ingestion cycle executed", "result": res})
                else:
                    self._send_json(404, {"error": "Not Found"})

        try:
            self.http_server = HTTPServer((host, port), IngestionControlHandler)
            self.http_thread = threading.Thread(target=self.http_server.serve_forever, daemon=True)
            self.http_thread.start()
            logger.info(f"Ingestion Service HTTP Control Server running at http://{host}:{port} (/health, /status, /trigger)")
        except Exception as exc:
            logger.warning(f"Could not start HTTP control server on port {port}: {exc}")

    def stop(self) -> None:
        """Signal the scheduler to gracefully stop."""
        self.running = False
        self._stop_event.set()
        if self.http_server:
            self.http_server.shutdown()
            self.http_server.server_close()
            logger.info("HTTP Control Server stopped.")
        if self.mock_server:
            self.mock_server.stop()
        logger.info("IngestionSchedulerService stop signal received.")

    def start_scheduler_loop(
        self,
        interval_minutes: Optional[int] = None,
        continuous: Optional[bool] = None,
        enable_http_server: bool = False,
        http_port: int = 8000,
    ) -> None:
        if interval_minutes is None:
            interval_minutes = int(self.config["ingestion"].get("schedule_minutes", 15))
        if continuous is None:
            continuous = bool(self.config["ingestion"].get("continuous_run", False))

        if enable_http_server:
            self.start_http_server(port=http_port)

        logger.info(f"Starting scheduler cycle (Interval: {interval_minutes}m, Continuous: {continuous})")
        self.run_cycle()

        if continuous:
            while self.running and not self._stop_event.is_set():
                if self._stop_event.wait(timeout=interval_minutes * 60):
                    break
                if self.running:
                    self.run_cycle()


def main() -> None:
    parser = argparse.ArgumentParser(description="ADAVIS Plant API Ingestion Service (Standalone Server)")
    parser.add_argument("--config", "-c", type=str, default=None, help="Path to custom ingestion_config.json")
    parser.add_argument("--once", action="store_true", help="Run a single ingestion cycle and exit immediately")
    parser.add_argument("--continuous", action="store_true", help="Run in continuous schedule loop")
    parser.add_argument("--interval", "-i", type=int, default=None, help="Override schedule interval in minutes (default 15)")
    parser.add_argument("--mode", "-m", type=str, choices=["APPEND", "TRUNCATE_AND_LOAD"], default=None, help="Ingestion mode")
    parser.add_argument("--mock", action="store_true", help="Force local mock server mode")
    parser.add_argument("--live", action="store_true", help="Force live Plant API mode")
    parser.add_argument("--mock-port", type=int, default=None, help="Port for local mock server (default 8001)")
    parser.add_argument("--http-server", action="store_true", default=True, help="Enable HTTP health/status control server")
    parser.add_argument("--no-http", dest="http_server", action="store_false", help="Disable HTTP control server")
    parser.add_argument("--http-port", type=int, default=8000, help="Port for HTTP control server (default 8000)")

    args = parser.parse_args()

    use_mock = None
    if args.mock:
        use_mock = True
    elif args.live:
        use_mock = False

    scheduler = IngestionSchedulerService(
        config_path=args.config,
        use_mock=use_mock,
        ingestion_mode=args.mode,
        mock_port=args.mock_port,
    )

    def _sig_handler(sig, frame):
        logger.info(f"Caught signal {sig}, initiating graceful shutdown...")
        scheduler.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT, _sig_handler)
    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, _sig_handler)

    continuous_mode = True if args.continuous else (False if args.once else None)

    try:
        scheduler.start_scheduler_loop(
            interval_minutes=args.interval,
            continuous=continuous_mode,
            enable_http_server=args.http_server,
            http_port=args.http_port,
        )
    except KeyboardInterrupt:
        scheduler.stop()


if __name__ == "__main__":
    main()
