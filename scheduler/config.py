#!/usr/bin/env python3
"""Configuration loader for IIoT multi-equipment continuous ingestion pipeline.

Parses backend/config/ingestion_config.yaml with dynamic environment variable
interpolation (${VAR:-default} and ${VAR}), providing typed settings for:
- 5 fixed target equipments (RMG, FBD, BLE, COAT, COMP)
- Ingestion mode (ONCE vs CONTINUOUS)
- Configurable scheduler intervals (15-minute / streaming)
- API endpoint connectivity
- Compression Excel watch directory & column mappings
- MongoDB and Redis cache connections
- MDB requirement decision gate (default: False)
"""

from __future__ import annotations

import logging
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

import yaml

logger = logging.getLogger(__name__)

ENV_VAR_PATTERN = re.compile(r"\$\{(\w+)(?::-(.*?))?\}")


def interpolate_env_vars(raw_text: str) -> str:
    """Replace ${VAR} and ${VAR:-default} patterns with environment values."""
    def _replace(match: re.Match) -> str:
        var_name = match.group(1)
        default_val = match.group(2) if match.group(2) is not None else ""
        return os.environ.get(var_name, default_val)

    return ENV_VAR_PATTERN.sub(_replace, raw_text)


@dataclass
class ApiSettings:
    enabled: bool = True
    base_url: str = "http://localhost:8000"
    endpoint: str = "/fwxapi/rest/v1/Dataset"
    timeout_seconds: int = 30
    auth_type: str = "NONE"
    auth_token: str = ""
    api_key: str = ""

    @property
    def full_url(self) -> str:
        base = self.base_url.rstrip("/")
        ep = self.endpoint.lstrip("/")
        if base.endswith("/Dataset"):
            return base
        return f"{base}/{ep}"


@dataclass
class DatabaseSettings:
    mongo_uri: str = "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin"
    mongo_database: str = "adavis_platform"
    redis_host: str = "localhost"
    redis_port: int = 8379
    redis_password: str = "Redis123!"
    redis_db: int = 0


@dataclass
class ScheduleSettings:
    interval_minutes: int = 15
    interval_seconds: int = 10
    execution_mode: str = "CONTINUOUS"  # "ONCE" or "CONTINUOUS"


@dataclass
class EquipmentItem:
    equipment_id: str
    equipment_code: str
    equipment_name: str
    equipment_type: str
    asset_id: str
    source_type: str  # "API" or "EXCEL"
    endpoint: str = ""
    source_path: str = ""
    sample_batch: str = ""
    sample_lot: str = ""
    sample_product: str = ""


@dataclass
class CompressionSettings:
    source_type: str = "EXCEL"
    source_path: str = "data/ingestion/compression"
    file_patterns: List[str] = field(default_factory=lambda: ["*.xls", "*.xlsx"])
    mdb_required: bool = False
    mdb_path: str = "sample/SawcData.mdb"
    column_mapping: Dict[str, str] = field(default_factory=lambda: {
        "batchNumber": "Lot No.",
        "lotNumber": "Lot No.",
        "productCode": "Product Code",
        "productName": "Product Name",
        "recipeName": "Recipe",
        "equipmentId": "Machine Name",
        "timestamp": "Date/Time",
    })
    parameters: List[Dict[str, Any]] = field(default_factory=list)


@dataclass
class IngestionSettings:
    environment: str = "LOCAL"
    execution_mode: str = "CONTINUOUS"
    schedule: ScheduleSettings = field(default_factory=ScheduleSettings)
    api: ApiSettings = field(default_factory=ApiSettings)
    database: DatabaseSettings = field(default_factory=DatabaseSettings)
    equipments: List[EquipmentItem] = field(default_factory=list)
    compression: CompressionSettings = field(default_factory=CompressionSettings)
    raw_config: Dict[str, Any] = field(default_factory=dict)

    def get_equipment(self, equipment_id: str) -> Optional[EquipmentItem]:
        for eq in self.equipments:
            if eq.equipment_id == equipment_id or eq.equipment_code == equipment_id:
                return eq
        return None

    def get_active_equipment_ids(self) -> List[str]:
        return [eq.equipment_id for eq in self.equipments]


def find_config_path(override_path: Optional[str] = None) -> Path:
    if override_path:
        return Path(override_path).resolve()

    env_path = os.getenv("INGESTION_CONFIG_FILE")
    if env_path:
        return Path(env_path).resolve()

    candidates = [
        Path(__file__).resolve().parent.parent / "config" / "ingestion_config.yaml",
        Path.cwd() / "backend" / "config" / "ingestion_config.yaml",
        Path.cwd() / "config" / "ingestion_config.yaml",
    ]
    for c in candidates:
        if c.is_file():
            return c
    return candidates[0]


def load_config(config_path: Optional[str] = None) -> IngestionSettings:
    """Load and parse the ingestion configuration."""
    cfg_file = find_config_path(config_path)

    raw_dict: Dict[str, Any] = {}
    if cfg_file.is_file():
        try:
            with open(cfg_file, "r", encoding="utf-8") as f:
                content = f.read()
            interpolated = interpolate_env_vars(content)
            raw_dict = yaml.safe_load(interpolated) or {}
            logger.debug("Successfully loaded ingestion config from %s", cfg_file)
        except Exception as ex:
            logger.warning("Failed to parse config file %s (%s). Using fallback defaults.", cfg_file, ex)
    else:
        logger.debug("Config file not found at %s. Using environment/defaults.", cfg_file)

    iiot_block = raw_dict.get("iiot", {})
    ingestion_block = iiot_block.get("ingestion", {})

    # 1. Schedule & execution mode
    sched_block = ingestion_block.get("schedule", {})
    exec_mode = (
        os.getenv("INGESTION_MODE")
        or ingestion_block.get("executionMode")
        or "CONTINUOUS"
    ).upper()

    schedule = ScheduleSettings(
        interval_minutes=int(os.getenv("INGESTION_INTERVAL_MINUTES") or sched_block.get("intervalMinutes", 15)),
        interval_seconds=int(os.getenv("INGESTION_INTERVAL_SECONDS") or sched_block.get("intervalSeconds", 10)),
        execution_mode=exec_mode,
    )

    # 2. REST API Settings
    api_block = ingestion_block.get("api", {})
    api_settings = ApiSettings(
        enabled=bool(api_block.get("enabled", True)),
        base_url=os.getenv("IIOT_API_BASE_URL") or api_block.get("baseUrl", "http://localhost:8000"),
        endpoint=os.getenv("IIOT_API_ENDPOINT") or api_block.get("endpoint", "/fwxapi/rest/v1/Dataset"),
        timeout_seconds=int(os.getenv("IIOT_API_TIMEOUT_SECONDS") or api_block.get("timeoutSeconds", 30)),
        auth_type=api_block.get("authentication", {}).get("type", "NONE"),
        auth_token=os.getenv("IIOT_API_AUTH_TOKEN") or api_block.get("authentication", {}).get("token", ""),
        api_key=os.getenv("IIOT_API_KEY") or api_block.get("authentication", {}).get("apiKey", ""),
    )

    # 3. Database Settings
    db_block = ingestion_block.get("database", {})
    mongo_block = db_block.get("mongodb", {})
    redis_block = db_block.get("redis", {})

    database = DatabaseSettings(
        mongo_uri=os.getenv("MONGODB_URI") or mongo_block.get("uri", "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin"),
        mongo_database=os.getenv("MONGODB_DATABASE") or mongo_block.get("databaseName", "adavis_platform"),
        redis_host=os.getenv("REDIS_HOST") or redis_block.get("host", "localhost"),
        redis_port=int(os.getenv("REDIS_PORT") or redis_block.get("port", 8379)),
        redis_password=os.getenv("REDIS_PASSWORD") or redis_block.get("password", "Redis123!"),
        redis_db=int(os.getenv("REDIS_DB") or redis_block.get("db", 0)),
    )

    # 4. Equipments
    eq_list = raw_dict.get("equipment", [])
    equipments: List[EquipmentItem] = []
    if eq_list:
        for item in eq_list:
            equipments.append(EquipmentItem(
                equipment_id=str(item.get("equipmentId", "")),
                equipment_code=str(item.get("equipmentCode", item.get("equipmentId", ""))),
                equipment_name=str(item.get("equipmentName", "")),
                equipment_type=str(item.get("equipmentType", "")),
                asset_id=str(item.get("assetId", "")),
                source_type=str(item.get("sourceType", "API")).upper(),
                endpoint=str(item.get("endpoint", "")),
                source_path=str(item.get("sourcePath", "")),
                sample_batch=str(item.get("sampleBatch", "")),
                sample_lot=str(item.get("sampleLot", "")),
                sample_product=str(item.get("sampleProduct", "")),
            ))
    else:
        # Fallback to the 5 default equipments
        equipments = [
            EquipmentItem("MB003", "MB003", "Rapid Mixer Granulator", "RMG", "10094", "API"),
            EquipmentItem("MB004", "MB004", "Fluid Bed Dryer", "FBD", "10110", "API"),
            EquipmentItem("MB005", "MB005", "Octagonal Blender", "BLE", "10095", "API"),
            EquipmentItem("MB041", "MB041", "Auto Coater", "COAT", "10141", "API"),
            EquipmentItem("MB040", "MB040", "Compression Machine", "COMPRESSION", "10040", "EXCEL", source_path="data/ingestion/compression"),
        ]

    # 5. Compression Settings
    comp_block = raw_dict.get("compression", {})
    comp_src = comp_block.get("source", {})
    compression = CompressionSettings(
        source_type=comp_src.get("type", "EXCEL").upper(),
        source_path=os.getenv("COMPRESSION_SOURCE_PATH") or comp_src.get("path", "data/ingestion/compression"),
        file_patterns=comp_src.get("filePatterns", ["*.xls", "*.xlsx"]),
        mdb_required=bool(os.getenv("COMPRESSION_MDB_REQUIRED", "false").lower() in ("true", "1", "yes")),
        mdb_path=os.getenv("COMPRESSION_MDB_PATH") or comp_src.get("mdbPath", "sample/SawcData.mdb"),
        column_mapping=comp_block.get("mapping", {
            "batchNumber": os.getenv("COMPRESSION_BATCH_COLUMN", "Lot No."),
            "lotNumber": os.getenv("COMPRESSION_LOT_COLUMN", "Lot No."),
            "productCode": os.getenv("COMPRESSION_PRODUCT_CODE_COLUMN", "Product Code"),
            "productName": os.getenv("COMPRESSION_PRODUCT_NAME_COLUMN", "Product Name"),
            "recipeName": os.getenv("COMPRESSION_RECIPE_COLUMN", "Recipe"),
            "equipmentId": os.getenv("COMPRESSION_EQUIPMENT_COLUMN", "Machine Name"),
            "timestamp": os.getenv("COMPRESSION_TIMESTAMP_COLUMN", "Date/Time"),
        }),
        parameters=comp_block.get("parameters", []),
    )

    return IngestionSettings(
        environment=raw_dict.get("environment", os.getenv("APP_ENVIRONMENT", "LOCAL")),
        execution_mode=exec_mode,
        schedule=schedule,
        api=api_settings,
        database=database,
        equipments=equipments,
        compression=compression,
        raw_config=raw_dict,
    )


# Global singleton instance
_CONFIG_INSTANCE: Optional[IngestionSettings] = None


def get_config() -> IngestionSettings:
    global _CONFIG_INSTANCE
    if _CONFIG_INSTANCE is None:
        _CONFIG_INSTANCE = load_config()
    return _CONFIG_INSTANCE
