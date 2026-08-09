#!/usr/bin/env python3
"""
Load IIOT master and realtime data from realtime_sample_data Excel files
into the existing MongoDB schema used by docker/seed_data_iiot_file.js.

Default target:
mongodb://admin:*****@localhost:37017/adavis_platform?authSource=admin&directConnection=true

Use --mongo-uri to override.
"""

from __future__ import annotations

import argparse
import math
import random
import re
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pandas as pd
from pymongo import MongoClient, UpdateOne


TENANT_ID = "TNT-0001"
DB_NAME = "adavis_platform"

DEFAULT_MONGO_URI = "mongodb://admin:*****@localhost:37017/adavis_platform?authSource=admin&directConnection=true"

PLANT_ID = "PLNT-0001"
BLOCK_ID = "BLK-0001"
AREA_ID = "AREA-0001"
ROOM_ID = "ROOM-0001"

EQUIPMENT_DEFS = [
    {
        "equipmentId": "WEG-003-PVII",
        "equipmentCode": "WEG-003-PVII",
        "equipmentName": "Wet Granulator #WEG-003",
        "equipmentType": "WEG",
        "equipmentTypeName": "Wet Granulator",
        "make": "GEA Pharma",
        "model": "WEG-003",
        "stageOrder": 1,
    },
    {
        "equipmentId": "FBD-004-PVII",
        "equipmentCode": "FBD-004-PVII",
        "equipmentName": "Fluid Bed Dryer #FBD-004",
        "equipmentType": "FBD",
        "equipmentTypeName": "Fluid Bed Dryer",
        "make": "Glatt",
        "model": "FBD-004",
        "stageOrder": 2,
    },
    {
        "equipmentId": "BLE-003-PVII",
        "equipmentCode": "BLE-003-PVII",
        "equipmentName": "Bin Blender #BLE-003",
        "equipmentType": "BLE",
        "equipmentTypeName": "Bin Blender",
        "make": "Bohle",
        "model": "BLE-003",
        "stageOrder": 3,
    },
]

BATCH_CATALOG = [
    {"batchNo": "AMR0026001", "recipeName": "CelecoxibCaps100mg200mgCB"},
    {"batchNo": "AMR0026002", "recipeName": "MirtazapineTablets15mg30mg45mgCB"},
    {"batchNo": "AMR0026003", "recipeName": "MirtazapineTablets15mg30mg45mgCB"},
    {"batchNo": "AMR0026004", "recipeName": "MirtazapineTablets15mg30mg45mgCB"},
    {"batchNo": "AMR0026005", "recipeName": "MirtazapineTablets15mg30mg45mgCB"},
    {"batchNo": "AMR0026006", "recipeName": "MirtazapineTablets15mg30mg45mgCB"},
    {"batchNo": "AMR0026007", "recipeName": "MirtazapineTablets15mg30mg45mgCB"},
]

RECIPE_PRODUCT_MAP = {
    "MirtazapineTablets15mg30mg45mgCB": {
        "productCode": "MIRTAZAPINE_TAB_15_30_45_CB",
        "productName": "Mirtazapine Tablets 15mg/30mg/45mg CB",
        "productCategory": "Tablets",
    },
    "CelecoxibCaps100mg200mgCB": {
        "productCode": "CELECOXIB_CAP_100_200_CB",
        "productName": "Celecoxib Capsules 100mg/200mg CB",
        "productCategory": "Capsules",
    },
}

EQUIPMENT_TYPE_DEFAULT_LOT = {
    "WEG": "LOT1",
    "FBD": "LOT2",
    "BLE": "LOT3",
}

EQUIPMENT_PREFIX_BY_TYPE = {
    "WEG": "PR_WEG_003_",
    "FBD": "PR_FBD_004_",
    "BLE": "PR_BLE_003_",
}

# Exclude identity/descriptor and count/flag fields from equipment parameter master/limits.
EXCLUDED_PARAMETER_KEYS = {
    "batchenable",
    "batchrequest",
    "batchstatus",
    "batchnumber",
    "batchnumberwip",
    "batchno",
    "recipename",
    "recipenamewip",
    "recipestate",
    "userid",
    "user",
    "stepname",
    "stepnameprocess",
    "stepnamewip",
    "stepno",
    "stepcounterprocess",
    "stepcounterwip",
    "processmanualautocsd",
    "processmanualautostatus",
    "ecfactprodphasenameptx",
    "ecwactwipphasenameptx",
    "ecfactprodphasestatussta",
    "ecwactwipphasestatussta",
    "ecfactprodphasetypeset",
    "ecwactwipphasetypeset",
    "ecfactprodphasenumberset",
    "ecwactwipphasenumberset",
    "epwwipphasenumberrequest",
    "epfprodprocenable",
    "epfprodprocenablecsd",
    "blendingstart",
    "blendingabort",
    "blendingresume",
    "status",
}

# Industry baseline limits for core CPP parameters. Fallback limits are computed from observations.
INDUSTRY_LIMITS: dict[str, dict[str, tuple[float, float]]] = {
    "WEG": {
        "agitatorSpeedPV": (8.5, 1.5),
        "agitatorTorque": (10.0, 6.0),
        "chopperSpeedPV": (1200.0, 250.0),
        "processTimePV": (900.0, 180.0),
    },
    "FBD": {
        "inletAirTemperaturePV": (65.0, 5.0),
        "productTemperaturePV": (45.0, 4.0),
        "inletAirFlowPV": (220.0, 40.0),
        "exhaustAirTempPV": (28.0, 5.0),
    },
    "BLE": {
        "blendingSpeedPV": (10.0, 2.0),
        "blendingRemainingTimeMin": (8.0, 2.0),
        "blendingSetTime": (15.0, 3.0),
    },
}


@dataclass
class EquipmentDef:
    equipment_id: str
    equipment_code: str
    equipment_name: str
    equipment_type: str
    equipment_type_name: str
    make: str
    model: str
    stage_order: int

    @property
    def hierarchy(self) -> dict[str, str]:
        full = f"{PLANT_ID}/{BLOCK_ID}/{AREA_ID}/{ROOM_ID}/{self.equipment_code}"
        return {
            "plant": PLANT_ID,
            "block": BLOCK_ID,
            "area": AREA_ID,
            "room": ROOM_ID,
            "fullPath": full,
        }


def now_utc() -> datetime:
    return datetime.now(UTC)


def to_camel_case(value: str) -> str:
    clean = re.sub(r"[^A-Za-z0-9]+", " ", value or "").strip()
    if not clean:
        return "field"
    parts = [p for p in clean.split() if p]
    if not parts:
        return "field"
    first = parts[0][:1].lower() + parts[0][1:]
    rest = [p[:1].upper() + p[1:] for p in parts[1:]]
    return first + "".join(rest)


def normalize_recipe_name(name: str | None) -> str:
    return re.sub(r"\s+", "", (name or "").strip())


def is_placeholder_recipe(name: str | None) -> bool:
    normalized = normalize_recipe_name(name)
    if not normalized or normalized in {"---", "NA", "N/A", "0"}:
        return True
    return bool(re.fullmatch(r"\d+(?:\.\d+)?", normalized))


def get_batch_recipe(batch_no: str, fallback: str | None = None) -> str:
    if not is_placeholder_recipe(fallback):
        return normalize_recipe_name(fallback)
    for row in BATCH_CATALOG:
        if row["batchNo"] == (batch_no or "").strip():
            return row["recipeName"]
    return normalize_recipe_name(fallback)


def get_product_from_recipe(recipe_name: str) -> dict[str, str]:
    normalized = normalize_recipe_name(recipe_name)
    if normalized in RECIPE_PRODUCT_MAP:
        mapped = RECIPE_PRODUCT_MAP[normalized]
        return {
            "recipeName": normalized,
            "productCode": mapped["productCode"],
            "productName": mapped["productName"],
            "productCategory": mapped["productCategory"],
        }
    category = "Capsules" if "caps" in normalized.lower() else "Tablets"
    code = re.sub(r"[^A-Za-z0-9]+", "_", normalized).upper() if normalized else "UNKNOWN_PRODUCT"
    name = re.sub(r"([a-z])([A-Z])", r"\1 \2", normalized) if normalized else "Unknown Product"
    name = re.sub(r"(\d+mg)", r" \1", name).strip()
    return {
        "recipeName": normalized,
        "productCode": code,
        "productName": name,
        "productCategory": category,
    }


def parse_lot_value(raw_lot: Any) -> str | None:
    if raw_lot is None or (isinstance(raw_lot, float) and math.isnan(raw_lot)):
        return None
    text = str(raw_lot).strip()
    if not text:
        return None
    m = re.search(r"LOT\s*([0-9A-Za-z]+)", text, flags=re.IGNORECASE)
    if m:
        return f"LOT{m.group(1).upper()}".replace("LOTLOT", "LOT")
    if re.match(r"^[0-9A-Za-z]+$", text, flags=re.IGNORECASE):
        return f"LOT{text.upper()}".replace("LOTLOT", "LOT")
    return None


def extract_batch_no(raw_batch: str, default_batch: str) -> str:
    text = (raw_batch or "").strip()
    if not text:
        return default_batch

    m_amr = re.search(r"(AMR\d{7})", text, flags=re.IGNORECASE)
    if m_amr:
        return m_amr.group(1).upper()

    m = re.match(r"^([A-Za-z0-9]+)(?:[-_ ]?LOT\s*([0-9A-Za-z]+))?$", text, flags=re.IGNORECASE)
    if m and m.group(1):
        return m.group(1).strip().upper()

    return default_batch


def parse_batch_and_lot(raw: Any, default_batch: str, eq_type: str, explicit_lot: Any = None) -> tuple[str, str]:
    text = str(raw).strip() if raw is not None and not (isinstance(raw, float) and math.isnan(raw)) else ""
    batch = extract_batch_no(text, default_batch)
    lot = parse_lot_value(explicit_lot) or EQUIPMENT_TYPE_DEFAULT_LOT.get(eq_type, "LOT1")

    if not text:
        return batch, lot

    m = re.match(r"^([A-Za-z0-9]+)(?:[-_ ]?LOT\s*([0-9A-Za-z]+))?$", text, flags=re.IGNORECASE)
    if m and m.group(2):
        lot = f"LOT{m.group(2).upper()}".replace("LOTLOT", "LOT")
    return batch, lot


def normalize_status(status: Any) -> str:
    text = str(status or "").strip().upper()
    if not text:
        return "RUNNING"
    if "STOP" in text or "COMP" in text:
        return "STOP"
    if "IDLE" in text or "HOLD" in text:
        return "IDLE"
    return "RUNNING"


def canonical_param_key(param_code: str, eq_type: str | None = None) -> str:
    raw = (param_code or "").strip()
    prefix = EQUIPMENT_PREFIX_BY_TYPE.get(eq_type or "", "")
    if prefix and raw.startswith(prefix):
        raw = raw[len(prefix):]
    return to_camel_case(raw)


def is_equipment_process_parameter(param_code: str, eq_type: str | None = None) -> bool:
    return canonical_param_key(param_code, eq_type).lower() not in EXCLUDED_PARAMETER_KEYS


def is_valid_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and not math.isnan(float(value))


def sanitize_value(value: Any) -> Any:
    if pd.isna(value):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, bool):
        return value
    text = str(value).strip()
    if not text:
        return None

    num_candidate = re.sub(r"[^0-9.+-]", "", text)
    if num_candidate:
        try:
            return float(num_candidate)
        except ValueError:
            pass

    if text.lower() in {"true", "false"}:
        return text.lower() == "true"

    return text


def parse_datetime(value: Any) -> datetime | None:
    if pd.isna(value):
        return None
    if isinstance(value, datetime):
        if value.tzinfo is None:
            return value.replace(tzinfo=UTC)
        return value.astimezone(UTC)

    try:
        dt = pd.to_datetime(value, utc=True)
        if pd.isna(dt):
            return None
        return dt.to_pydatetime()
    except Exception:
        return None


def equipment_list() -> list[EquipmentDef]:
    defs: list[EquipmentDef] = []
    for row in EQUIPMENT_DEFS:
        defs.append(
            EquipmentDef(
                equipment_id=row["equipmentId"],
                equipment_code=row["equipmentCode"],
                equipment_name=f"EQP-{row['equipmentCode']}",
                equipment_type=row["equipmentType"],
                equipment_type_name=row["equipmentTypeName"],
                make=row["make"],
                model=row["model"],
                stage_order=row["stageOrder"],
            )
        )
    return defs


def get_timeseries_cpp_collection(eq: EquipmentDef) -> str:
    norm = re.sub(r"[^a-z0-9]+", "_", eq.equipment_id.lower())
    return f"iiot_ts_cpp_tnt_0001_{norm}"


def get_timeseries_alarm_collection(eq: EquipmentDef) -> str:
    norm = re.sub(r"[^a-z0-9]+", "_", eq.equipment_id.lower())
    return f"iiot_ts_alarm_event_tnt_0001_{norm}"


def create_indexes(db) -> None:
    db.iiot_equiment_master.create_index([("tenantId", 1), ("equipmentId", 1)], unique=True)
    db.iiot_equiment_master.create_index([("plantId", 1), ("blockId", 1), ("areaId", 1), ("roomId", 1)])
    db.iiot_equiment_master.create_index([("equipmentType", 1)])

    db.iiot_equipment_critical_parameters.create_index(
        [("tenantId", 1), ("equipmentId", 1), ("parameterId", 1)], unique=True
    )
    db.iiot_equipment_critical_parameters_limit.create_index(
        [("tenantId", 1), ("equipmentId", 1), ("parameterId", 1), ("effectiveFrom", -1)]
    )

    db.iiot_product_master.create_index([("tenantId", 1), ("productId", 1)], unique=True)
    db.iiot_source_table_mapping.create_index([("tenantId", 1), ("equipmentId", 1)], unique=True)
    db.iiot_ingestion_checkpoint.create_index([("equipmentId", 1), ("streamType", 1)], unique=True)
    db.iiot_ingestion_job_run.create_index([("equipmentId", 1), ("startedAt", -1)])
    db.iiot_equipment_live_status.create_index([("equipmentId", 1)], unique=True)
    db.iiot_batch_summary.create_index(
        [("tenantId", 1), ("plantId", 1), ("areaId", 1), ("equipmentId", 1), ("batchNo", 1)],
        unique=True,
    )


def maybe_reset_collections(db, eq_defs: list[EquipmentDef], reset: bool) -> None:
    if not reset:
        return

    core = [
        "iiot_equiment_master",
        "iiot_equipment_critical_parameters",
        "iiot_equipment_critical_parameters_limit",
        "iiot_product_master",
        "iiot_source_table_mapping",
        "iiot_ingestion_checkpoint",
        "iiot_ingestion_job_run",
        "iiot_equipment_live_status",
        "iiot_batch_summary",
    ]
    ts = []
    for eq in eq_defs:
        ts.append(get_timeseries_cpp_collection(eq))
        ts.append(get_timeseries_alarm_collection(eq))

    for name in core + ts:
        db[name].delete_many({})


def find_column(columns: list[str], pattern: str) -> str | None:
    regex = re.compile(pattern, re.IGNORECASE)
    for c in columns:
        if regex.search(c):
            return c
    return None


def sheet_to_records(
    df: pd.DataFrame, eq_type: str, batch_sheet_name: str, prefix: str
) -> tuple[list[dict[str, Any]], set[str]]:
    rows: list[dict[str, Any]] = []
    parameter_keys: set[str] = set()
    columns = [str(c).strip() for c in df.columns]

    dt_col = find_column(columns, r"^DateTimeField$")
    if not dt_col:
        return rows, parameter_keys

    recipe_col = find_column(columns, r"Recipe_Name|RECIPE_NAME")
    operator_col = find_column(columns, r"User_ID|_USER$")
    status_col = find_column(columns, r"Batch_Status|Process_Manual_Auto_Status|Recipe_State")
    batch_col = find_column(columns, r"Batch_No|Batch_Number")
    lot_col = find_column(columns, r"Lot_No|LotNumber|Lot_Number|\bLot\b")

    for col in columns:
        if col == dt_col:
            continue
        raw_code = col.strip()
        if raw_code and is_equipment_process_parameter(raw_code, eq_type):
            parameter_keys.add(raw_code)

    for _, row in df.iterrows():
        observed_at = parse_datetime(row.get(dt_col))
        if not observed_at:
            continue

        raw_batch_field = row.get(batch_col) if batch_col else None
        raw_lot_field = row.get(lot_col) if lot_col else None
        batch_no, lot_no = parse_batch_and_lot(raw_batch_field, batch_sheet_name, eq_type, raw_lot_field)

        recipe_name_raw = row.get(recipe_col) if recipe_col else None
        operator_raw = row.get(operator_col) if operator_col else None
        status_raw = row.get(status_col) if status_col else None

        metrics: dict[str, Any] = {}
        for col in columns:
            if col == dt_col:
                continue
            val = sanitize_value(row.get(col))
            if val is None:
                continue

            raw_code = col.strip()
            if not is_equipment_process_parameter(raw_code, eq_type):
                continue
            metrics[raw_code] = val

        rows.append(
            {
                "equipmentType": eq_type,
                "batchNo": batch_no,
                "lotNo": lot_no,
                "observedAt": observed_at,
                "recipeNameRaw": normalize_recipe_name(str(recipe_name_raw or "")),
                "operatorNameRaw": str(operator_raw or "").strip(),
                "statusRaw": status_raw,
                "metrics": metrics,
            }
        )

    return rows, parameter_keys


def load_excel_records(base_dir: Path) -> tuple[list[dict[str, Any]], dict[str, set[str]]]:
    files = [
        (base_dir / "WEG.xlsx", "WEG", "PR_WEG_003_"),
        (base_dir / "FBD.xlsx", "FBD", "PR_FBD_004_"),
        (base_dir / "BLE (1).xlsx", "BLE", "PR_BLE_003_"),
    ]

    all_rows: list[dict[str, Any]] = []
    parameter_keys_by_type: dict[str, set[str]] = {}
    for file_path, eq_type, prefix in files:
        if not file_path.exists():
            continue

        xls = pd.ExcelFile(file_path, engine="openpyxl")
        for sheet in xls.sheet_names:
            df = pd.read_excel(file_path, sheet_name=sheet, engine="openpyxl")
            rows, keys = sheet_to_records(df, eq_type, sheet, prefix)
            all_rows.extend(rows)
            parameter_keys_by_type.setdefault(eq_type, set()).update(keys)

    return all_rows, parameter_keys_by_type


def upsert_many(db, collection_name: str, docs: list[dict[str, Any]], key: str) -> None:
    if not docs:
        return
    ops: list[UpdateOne] = []
    for doc in docs:
        ops.append(UpdateOne({key: doc[key]}, {"$set": doc}, upsert=True))
    db[collection_name].bulk_write(ops, ordered=False)


def upsert_many_composite(db, collection_name: str, docs: list[dict[str, Any]], keys: list[str]) -> None:
    if not docs:
        return
    ops: list[UpdateOne] = []
    for doc in docs:
        filt = {k: doc[k] for k in keys}
        ops.append(UpdateOne(filt, {"$set": doc}, upsert=True))
    db[collection_name].bulk_write(ops, ordered=False)


def build_master_data(eq_defs: list[EquipmentDef]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    ts = now_utc()
    equipment_docs: list[dict[str, Any]] = []
    source_docs: list[dict[str, Any]] = []

    for idx, eq in enumerate(eq_defs, start=1):
        equipment_docs.append(
            {
                "equipmentSeqId": 10000 + idx,
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "blockId": BLOCK_ID,
                "areaId": AREA_ID,
                "roomId": ROOM_ID,
                "equipmentId": eq.equipment_id,
                "equipmentCode": eq.equipment_code,
                "equipmentName": eq.equipment_name,
                "equipmentType": eq.equipment_type,
                "equipmentTypeName": eq.equipment_type_name,
                "make": eq.make,
                "model": eq.model,
                "isActive": True,
                "isDeleted": False,
                "createdAt": ts,
                "updatedAt": ts,
                "hierarchy": eq.hierarchy,
                "equipmentLocation": eq.hierarchy["fullPath"],
            }
        )

        source_docs.append(
            {
                "mappingId": f"MAP-{TENANT_ID}-{eq.equipment_code}",
                "tenantId": TENANT_ID,
                "equipmentId": eq.equipment_id,
                "batchSource": {
                    "dbType": "SAP_HANA",
                    "schemaName": "SKPharma",
                    "tableName": f"SKPharma::CDSSKPharma.B_UDA_{eq.equipment_code}",
                    "sequenceColumn": "SerialNumber",
                    "timestampColumn": "LastModifiedTime",
                },
                "alarmEventSource": {
                    "dbType": "SAP_HANA",
                    "schemaName": "SKPharma",
                    "tableName": f"SKPharma::CDSSKPharma.AE_{eq.equipment_code}",
                    "sequenceColumn": "id",
                    "timestampColumn": "LastModifiedTime",
                },
                "pollIntervalSeconds": 30,
                "batchSize": 1000,
                "connectionRef": "SAP-HANA-DEV-01",
                "validationStatus": "SUCCESS",
                "lastValidatedAt": ts,
                "isActive": True,
                "updatedAt": ts,
                "hierarchy": eq.hierarchy,
            }
        )

    return equipment_docs, source_docs


def build_products(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    ts = now_utc()
    seen: set[str] = set()
    products: list[dict[str, Any]] = []

    # ensure catalog products are present
    for row in BATCH_CATALOG:
        p = get_product_from_recipe(row["recipeName"])
        if p["productCode"] in seen:
            continue
        seen.add(p["productCode"])
        products.append(
            {
                "productId": p["productCode"],
                "productCode": p["productCode"],
                "productName": p["productName"],
                "productCategory": p["productCategory"],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "isActive": True,
                "createdAt": ts,
                "updatedAt": ts,
            }
        )

    for rec in records:
        recipe = get_batch_recipe(rec["batchNo"], rec.get("recipeNameRaw"))
        p = get_product_from_recipe(recipe)
        if p["productCode"] in seen:
            continue
        seen.add(p["productCode"])
        products.append(
            {
                "productId": p["productCode"],
                "productCode": p["productCode"],
                "productName": p["productName"],
                "productCategory": p["productCategory"],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "isActive": True,
                "createdAt": ts,
                "updatedAt": ts,
            }
        )

    return products


def infer_param_type(values: list[Any]) -> tuple[str, bool]:
    numeric_count = sum(1 for v in values if is_valid_number(v))
    if numeric_count > 0 and numeric_count >= max(1, int(len(values) * 0.7)):
        return "FLOAT", True
    bool_count = sum(1 for v in values if isinstance(v, bool))
    if bool_count > 0 and bool_count >= max(1, int(len(values) * 0.7)):
        return "BOOLEAN", False
    return "STRING", False


def unit_for_param(param_key: str) -> str:
    key = canonical_param_key(param_key).lower()

    # Explicit mappings across WEG/FBD/BLE realtime parameters.
    exact_units = {
        # Identity / descriptors
        "batchnumber": "code",
        "batchnumberwip": "code",
        "batchno": "code",
        "recipename": "text",
        "recipenamewip": "text",
        "stepname": "text",
        "stepnameprocess": "text",
        "stepnamewip": "text",
        "userid": "code",
        "user": "code",
        "batchstatus": "state",
        "recipestate": "state",
        "processmanualautocsd": "state",
        "processmanualautostatus": "state",
        "ecfactprodphasenameptx": "text",
        "ecwactwipphasenameptx": "text",
        "ecfactprodphasestatussta": "state",
        "ecwactwipphasestatussta": "state",
        "ecfactprodphasetypeset": "code",
        "ecwactwipphasetypeset": "code",

        # Counts / flags
        "stepno": "count",
        "stepcounterprocess": "count",
        "stepcounterwip": "count",
        "ecfactprodphasenumberset": "count",
        "ecwactwipphasenumberset": "count",
        "epwwipphasenumberrequest": "count",
        "batchenable": "flag",
        "batchrequest": "flag",
        "epfprodprocenable": "flag",
        "epfprodprocenablecsd": "flag",
        "blendingstart": "flag",
        "blendingabort": "flag",
        "blendingresume": "flag",

        # WEG
        "agitatorcurrent": "amp",
        "choppercurrent": "amp",
        "rotorsievecurrent": "amp",
        "agitatorspeedpv": "rpm",
        "agitatorspeedsv": "rpm",
        "chopperspeedpv": "rpm",
        "chopperspeedsv": "rpm",
        "rotorsievespeedpv": "rpm",
        "rotorsievespeedsv": "rpm",
        "agitatortorque": "%",
        "agitatortorquedlymaxpv": "%",
        "agitatortorquedlymaxsv": "%",
        "agitatortorquemax": "%",
        "agitatortorquemin": "%",
        "agitatorpausetimepv": "sec",
        "agitatorpausetimesv": "sec",
        "agitatorruntimepv": "sec",
        "agitatorruntimesv": "sec",
        "spraypausepv": "sec",
        "spraypausesv": "sec",
        "spraytimepv": "sec",
        "spraytimesv": "sec",
        "operationtimeprocesspv": "sec",
        "processtimepv": "sec",
        "operationtime": "sec",
        "processtime": "sec",

        # FBD
        "dehumidificationtemppv": "celsius",
        "dehumidificationtempsv": "celsius",
        "inletairtemperaturepv": "celsius",
        "inletairtemperaturesv": "celsius",
        "inletairtemperaturemaxset": "celsius",
        "inletairtemperatureminset": "celsius",
        "producttemperaturepv": "celsius",
        "producttemperaturemaxset": "celsius",
        "producttemperatureminset": "celsius",
        "exhaustairtemppv": "celsius",
        "exhaustairtempmaxset": "celsius",
        "exhaustairtempminset": "celsius",
        "p01preheatertempset": "celsius",
        "inletairflowpv": "m3/hr",
        "inletairflowsv": "m3/hr",
        "inletairhumidityrelativepv": "%",
        "inletairmodulatingflappv": "%",
        "inletairmodulatingflapsv": "%",
        "exhaustairfanspeedpv": "rpm",

        # BLE
        "blendingspeedpv": "rpm",
        "blendingspeedsv": "rpm",
        "blendingsettime": "min",
        "settime": "min",
        "blendingremainingtimemin": "min",
        "blendingremainingtimesec": "sec",
    }
    if key in exact_units:
        return exact_units[key]

    # Pattern-based fallback mappings.
    if "temperature" in key or "temp" in key:
        return "celsius"
    if "modulatingflap" in key:
        return "%"
    if "humidity" in key:
        return "%"
    if "speed" in key:
        return "rpm"
    if "torque" in key:
        return "%"
    if "counter" in key or "stepno" in key:
        return "count"
    if "status" in key or "state" in key:
        return "state"
    if "name" in key:
        return "text"
    if "user" in key or "id" in key:
        return "code"
    if "flow" in key:
        return "m3/hr"
    if "remainingtimemin" in key:
        return "min"
    if "timesec" in key:
        return "sec"
    if "time" in key:
        return "sec"
    return "unit"


def industry_limit_profile(eq_type: str, canonical_key: str, unit: str, samples: list[float]) -> tuple[float, float, float]:
    """
    Return (center, warn_delta, crit_delta) using deterministic industry-style profiles.
    """
    center = samples[len(samples) // 2] if samples else 0.0

    eq_map = INDUSTRY_LIMITS.get(eq_type, {})
    if canonical_key in eq_map:
        base, warn_delta = eq_map[canonical_key]
        return base, warn_delta, warn_delta * 1.5

    if unit == "celsius":
        return center, 3.0, 5.0
    if unit == "rpm":
        base = center if center else 100.0
        return base, max(base * 0.10, 5.0), max(base * 0.20, 10.0)
    if unit == "amp":
        base = center if center else 5.0
        return base, max(base * 0.15, 0.5), max(base * 0.30, 1.0)
    if unit == "m3/hr":
        base = center if center else 100.0
        return base, max(base * 0.12, 5.0), max(base * 0.25, 10.0)
    if unit == "%":
        base = center if center else 50.0
        return base, 10.0, 20.0
    if unit == "sec":
        base = center if center else 60.0
        return base, max(base * 0.15, 5.0), max(base * 0.30, 10.0)
    if unit == "min":
        base = center if center else 10.0
        return base, max(base * 0.15, 1.0), max(base * 0.30, 2.0)

    base = center if center else 1.0
    return base, max(abs(base) * 0.10, 0.1), max(abs(base) * 0.20, 0.2)


def build_parameter_and_limit_docs(
    records: list[dict[str, Any]],
    eq_defs_by_type: dict[str, EquipmentDef],
    parameter_keys_by_type: dict[str, set[str]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    ts = now_utc()
    param_docs: list[dict[str, Any]] = []
    limit_docs: list[dict[str, Any]] = []

    grouped: dict[str, dict[str, list[Any]]] = {}
    for rec in records:
        eq_type = rec["equipmentType"]
        grouped.setdefault(eq_type, {})
        for k, v in rec.get("metrics", {}).items():
            if not is_equipment_process_parameter(k, eq_type):
                continue
            grouped[eq_type].setdefault(k, []).append(v)

    for eq_type, keys in parameter_keys_by_type.items():
        grouped.setdefault(eq_type, {})
        for k in keys:
            if not is_equipment_process_parameter(k, eq_type):
                continue
            grouped[eq_type].setdefault(k, [])

    for eq_type, key_values in grouped.items():
        eq = eq_defs_by_type.get(eq_type)
        if not eq:
            continue

        p_idx = 0
        for key in sorted(key_values.keys()):
            values = key_values[key]
            p_type, is_critical = infer_param_type(values)
            p_idx += 1

            canonical_key = canonical_param_key(key, eq_type)
            parameter_id = key
            parameter_code = key
            parameter_name = key.replace("PR_", "PR-", 1) if key.startswith("PR_") else f"PR-{key}"
            unit = unit_for_param(key)

            param_docs.append(
                {
                    "parameterSeqId": 50000 + eq.stage_order * 1000 + p_idx,
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "equipmentId": eq.equipment_id,
                    "parameterId": parameter_id,
                    "parameterCode": parameter_code,
                    "parameterName": parameter_name,
                    "parameterType": p_type,
                    "parameterKey": key,
                    "canonicalParameterKey": canonical_key,
                    "unitOfMeasure": unit,
                    "isCritical": is_critical,
                    "isActive": True,
                    "createdAt": ts,
                    "updatedAt": ts,
                }
            )

            numeric_values = [float(v) for v in values if is_valid_number(v)]
            has_numeric_values = p_type == "FLOAT" and bool(numeric_values)

            if has_numeric_values:
                numeric_values = sorted(numeric_values)
                center, warn_delta, crit_delta = industry_limit_profile(eq_type, canonical_key, unit, numeric_values)
                low_warning = center - warn_delta
                high_warning = center + warn_delta
                low_critical = center - crit_delta
                high_critical = center + crit_delta
                ideal_min = center - warn_delta / 2
                ideal_max = center + warn_delta / 2
            else:
                center = 0.0
                low_critical = 0.0
                low_warning = 0.0
                ideal_min = 0.0
                ideal_max = 0.0
                high_warning = 0.0
                high_critical = 0.0

            limit_id = f"LIM-{eq_type}-{canonical_key}-{p_idx:03d}"
            limit_parameter_code = f"LIM-{parameter_code}"
            limit_parameter_name = f"LIM-{parameter_name}"
            limit_docs.append(
                {
                    "parameterLimitId": limit_id,
                    "parameterLimitCode": limit_id,
                    "parameterLimitSeqId": 90000 + eq.stage_order * 1000 + p_idx,
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "equipmentId": eq.equipment_id,
                    "parameterId": parameter_id,
                    "parameterCode": limit_parameter_code,
                    "parameterName": limit_parameter_name,
                    "parameterType": p_type,
                    "parameterKey": key,
                    "canonicalParameterKey": canonical_key,
                    "floatValue": round(center, 3),
                    "lowCriticalValue": round(low_critical, 3),
                    "lowWarningValue": round(low_warning, 3),
                    "idealMinValue": round(ideal_min, 3),
                    "idealMaxValue": round(ideal_max, 3),
                    "highWarningValue": round(high_warning, 3),
                    "highCriticalValue": round(high_critical, 3),
                    "alarmEnabled": p_type == "FLOAT",
                    "booleanValue": False,
                    "enumValue": "",
                    "stringValue": "",
                    "effectiveFrom": datetime(2026, 1, 1, tzinfo=UTC),
                    "effectiveTo": None,
                    "isActive": True,
                    "createdAt": ts,
                    "updatedAt": ts,
                }
            )

    return param_docs, limit_docs


def build_alarm_event(
    eq: EquipmentDef,
    when: datetime,
    source_seq: int,
    batch_no: str,
    lot_no: str,
    product: dict[str, str],
    operator_name: str,
    status: str,
    category: str,
    code: str,
    text: str,
    severity: str,
) -> dict[str, Any]:
    return {
        "eventAt": when,
        "meta": {
            "equipmentId": eq.equipment_id,
            "batchNo": batch_no,
            "lotNo": lot_no,
            "productName": product["productName"],
            "productCode": product["productCode"],
            "operatorName": operator_name,
            "supervisorName": operator_name,
            "equipmentType": eq.equipment_type,
            "equipmentName": eq.equipment_name,
            "equipmentLocation": eq.hierarchy["fullPath"],
            "status": status,
            "dateDay": when.day,
            "dayMonth": when.month,
            "dayYear": when.year,
            "timeHH": f"{when.hour:02d}",
            "timeMM": f"{when.minute:02d}",
            "timeSS": f"{when.second:02d}",
        },
        "source": {
            "tableName": f"SKPharma::CDSSKPharma.AE_{eq.equipment_code}",
            "sourceSeqId": source_seq,
            "lastModifiedTime": when,
            "machineDate": when.replace(tzinfo=None).isoformat(sep=" ", timespec="seconds"),
        },
        "event": {
            "eventCategory": category,
            "eventCode": code,
            "eventText": text,
            "severity": severity,
            "eventState": "OPEN" if category == "ALARM" else "INFO",
            "alarmAll": f";{code};" if category == "ALARM" else "",
            "eventAll": f";{code};" if category == "EVENT" else "",
        },
        "ingestedAt": now_utc(),
    }


def load_realtime_data(
    db,
    records: list[dict[str, Any]],
    eq_defs_by_type: dict[str, EquipmentDef],
    product_by_code: dict[str, dict[str, Any]],
    limit_lookup: dict[tuple[str, str], dict[str, Any]],
) -> dict[str, int]:
    grouped: dict[str, list[dict[str, Any]]] = {}
    for rec in records:
        eq_type = rec["equipmentType"]
        grouped.setdefault(eq_type, []).append(rec)

    checkpoint_docs = []
    job_docs = []
    batch_summaries = []
    live_status_docs = []

    cpp_total = 0
    alarm_total = 0
    event_total = 0

    pipeline: dict[str, dict[str, Any]] = {}

    for eq_type, eq_rows in grouped.items():
        eq = eq_defs_by_type.get(eq_type)
        if not eq:
            continue

        eq_rows.sort(key=lambda x: x["observedAt"])

        cpp_collection = db[get_timeseries_cpp_collection(eq)]
        alarm_collection = db[get_timeseries_alarm_collection(eq)]

        cpp_docs = []
        per_batch: dict[str, dict[str, Any]] = {}

        seq_base = 100000 + eq.stage_order * 1000000
        alarm_seq = 200000 + eq.stage_order * 1000000

        for i, rec in enumerate(eq_rows, start=1):
            observed_at = rec["observedAt"]
            batch_no = (rec.get("batchNo") or "").strip()
            if not batch_no:
                continue

            lot_no = rec.get("lotNo") or EQUIPMENT_TYPE_DEFAULT_LOT.get(eq_type, "LOT1")
            recipe_name = get_batch_recipe(batch_no, rec.get("recipeNameRaw"))
            product = get_product_from_recipe(recipe_name)
            product_doc = product_by_code.get(product["productCode"], product)

            operator_name = rec.get("operatorNameRaw") or "SYSTEM"
            status = normalize_status(rec.get("statusRaw") or rec.get("metrics", {}).get("batchStatus"))

            metrics = dict(rec.get("metrics", {}))

            cpp_docs.append(
                {
                    "observedAt": observed_at,
                    "meta": {
                        "tenantId": TENANT_ID,
                        "equipmentId": eq.equipment_id,
                        "batchNo": batch_no,
                        "lotNo": lot_no,
                        "productName": product_doc["productName"],
                        "productCode": product_doc["productCode"],
                        "productCategory": product_doc.get("productCategory", product["productCategory"]),
                        "recipeName": product["recipeName"],
                        "operatorName": operator_name,
                        "supervisorName": operator_name,
                        "equipmentType": eq.equipment_type,
                        "equipmentName": eq.equipment_name,
                        "equipmentLocation": eq.hierarchy["fullPath"],
                        "status": status,
                        "dateDay": observed_at.day,
                        "dayMonth": observed_at.month,
                        "dayYear": observed_at.year,
                        "timeHH": f"{observed_at.hour:02d}",
                        "timeMM": f"{observed_at.minute:02d}",
                        "timeSS": f"{observed_at.second:02d}",
                    },
                    "source": {
                        "tableName": f"SKPharma::CDSSKPharma.B_UDA_{eq.equipment_code}",
                        "sourceSeqId": seq_base + i,
                        "lastModifiedTime": observed_at,
                        "machineDate": observed_at.replace(tzinfo=None).isoformat(sep=" ", timespec="seconds"),
                    },
                    "metrics": metrics,
                    "ingestedAt": now_utc(),
                }
            )
            cpp_total += 1

            stats = per_batch.setdefault(
                batch_no,
                {
                    "start": observed_at,
                    "end": observed_at,
                    "lotNo": lot_no,
                    "operator": operator_name,
                    "product": product_doc,
                    "cpp": 0,
                    "alarms": 0,
                    "events": 0,
                    "recipes": set(),
                },
            )
            stats["cpp"] += 1
            if observed_at < stats["start"]:
                stats["start"] = observed_at
            if observed_at > stats["end"]:
                stats["end"] = observed_at
            stats["recipes"].add(product_doc["productCode"])

        for batch_no, stats in per_batch.items():
            batch_summaries.append(
                {
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "blockId": BLOCK_ID,
                    "areaId": AREA_ID,
                    "roomId": ROOM_ID,
                    "equipmentId": eq.equipment_id,
                    "batchNo": batch_no,
                    "lotNo": stats["lotNo"],
                    "productName": stats["product"]["productName"],
                    "productCode": stats["product"]["productCode"],
                    "equipmentType": eq.equipment_type,
                    "equipmentName": eq.equipment_name,
                    "equipmentLocation": eq.hierarchy["fullPath"],
                    "batchSize": "",
                    "operatorName": stats["operator"],
                    "supervisorName": stats["operator"],
                    "batchStartAt": stats["start"],
                    "batchEndAt": stats["end"],
                    "batchStatus": "COMPLETED",
                    "cppRecordCount": stats["cpp"],
                    "alarmCount": 0,
                    "eventCount": 0,
                    "recipeCodes": sorted(stats["recipes"]),
                    "productionCount": random.randint(1000, 5000),
                    "createdAt": now_utc(),
                    "updatedAt": now_utc(),
                    "hierarchy": eq.hierarchy,
                }
            )

            agg = pipeline.setdefault(
                batch_no,
                {
                    "start": stats["start"],
                    "end": stats["end"],
                    "product": stats["product"],
                    "operator": stats["operator"],
                    "cpp": 0,
                    "alarms": 0,
                    "events": 0,
                    "stage": {},
                },
            )
            if stats["start"] < agg["start"]:
                agg["start"] = stats["start"]
            if stats["end"] > agg["end"]:
                agg["end"] = stats["end"]
            agg["cpp"] += stats["cpp"]
            agg["alarms"] += stats["alarms"]
            agg["events"] += stats["events"]
            agg["stage"][eq.equipment_type] = "COMPLETED"

        if cpp_docs:
            cpp_collection.insert_many(cpp_docs, ordered=False)

        if eq_rows:
            latest_row = eq_rows[-1]
            latest_observed_at = latest_row["observedAt"]
            latest_batch_no = (latest_row.get("batchNo") or "").strip()
            latest_lot_no = latest_row.get("lotNo") or EQUIPMENT_TYPE_DEFAULT_LOT.get(eq_type, "LOT1")
            latest_recipe_name = get_batch_recipe(latest_batch_no, latest_row.get("recipeNameRaw"))
            latest_product = get_product_from_recipe(latest_recipe_name)
            latest_product_doc = product_by_code.get(latest_product["productCode"], latest_product)
            latest_operator = latest_row.get("operatorNameRaw") or "SYSTEM"
            latest_status = normalize_status(
                latest_row.get("statusRaw") or latest_row.get("metrics", {}).get("batchStatus")
            )

            live_status_docs.append(
                {
                    "equipmentId": eq.equipment_id,
                    "equipmentType": eq.equipment_type,
                    "equipmentCode": eq.equipment_code,
                    "equipmentName": eq.equipment_name,
                    "equipmentLocation": eq.hierarchy["fullPath"],
                    "batchNumber": latest_batch_no,
                    "lotNumber": latest_lot_no,
                    "status": latest_status,
                    "currentState": latest_status,
                    "stateReason": latest_status,
                    "lastBatchNo": latest_batch_no,
                    "lastLotNo": latest_lot_no,
                    "lastProductName": latest_product_doc["productName"],
                    "lastOperatorName": latest_operator,
                    "lastSourceSeqId": seq_base + len(eq_rows),
                    "lastEventAt": latest_observed_at,
                    "heartbeatAt": now_utc(),
                    "updatedAt": now_utc(),
                    "createdAt": now_utc(),
                }
            )

        checkpoint_docs.append(
            {
                "checkpointId": f"CP-{eq.equipment_id}-BATCH_CPP",
                "equipmentId": eq.equipment_id,
                "streamType": "BATCH_CPP",
                "sourceTable": f"SKPharma::CDSSKPharma.B_UDA_{eq.equipment_code}",
                "lastProcessedSeqId": seq_base + len(eq_rows),
                "lastProcessedAt": now_utc(),
                "status": "SUCCESS",
                "updatedAt": now_utc(),
            }
        )
        checkpoint_docs.append(
            {
                "checkpointId": f"CP-{eq.equipment_id}-ALARM_EVENT",
                "equipmentId": eq.equipment_id,
                "streamType": "ALARM_EVENT",
                "sourceTable": f"SKPharma::CDSSKPharma.AE_{eq.equipment_code}",
                "lastProcessedSeqId": alarm_seq,
                "lastProcessedAt": now_utc(),
                "status": "SUCCESS",
                "updatedAt": now_utc(),
            }
        )

        job_docs.append(
            {
                "jobRunId": f"JOB-REALTIME-{eq.equipment_type}-CPP",
                "equipmentId": eq.equipment_id,
                "streamType": "BATCH_CPP",
                "windowStartSeqId": seq_base + 1,
                "windowEndSeqId": seq_base + len(eq_rows),
                "recordsRead": len(eq_rows),
                "recordsWritten": len(eq_rows),
                "recordsSkipped": 0,
                "status": "SUCCESS",
                "startedAt": now_utc(),
                "completedAt": now_utc(),
                "createdAt": now_utc(),
                "updatedAt": now_utc(),
            }
        )
        job_docs.append(
            {
                "jobRunId": f"JOB-REALTIME-{eq.equipment_type}-ALARM",
                "equipmentId": eq.equipment_id,
                "streamType": "ALARM_EVENT",
                "windowStartSeqId": 1,
                "windowEndSeqId": alarm_seq,
                "recordsRead": 0,
                "recordsWritten": 0,
                "recordsSkipped": 0,
                "status": "SUCCESS",
                "startedAt": now_utc(),
                "completedAt": now_utc(),
                "createdAt": now_utc(),
                "updatedAt": now_utc(),
            }
        )

    for batch_no, agg in pipeline.items():
        batch_summaries.append(
            {
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "blockId": BLOCK_ID,
                "areaId": AREA_ID,
                "roomId": ROOM_ID,
                "equipmentId": f"PIPELINE-{batch_no}",
                "batchNo": batch_no,
                "lotNo": "LOT-PIPELINE",
                "productName": agg["product"]["productName"],
                "productCode": agg["product"]["productCode"],
                "equipmentType": "PIPELINE",
                "equipmentName": "WEG->FBD->BLE Pipeline",
                "equipmentLocation": f"{PLANT_ID}/{BLOCK_ID}/{AREA_ID}/{ROOM_ID}/PIPELINE",
                "batchSize": "",
                "operatorName": agg["operator"],
                "supervisorName": agg["operator"],
                "batchStartAt": agg["start"],
                "batchEndAt": agg["end"],
                "batchStatus": "COMPLETED",
                "cppRecordCount": agg["cpp"],
                "alarmCount": agg["alarms"],
                "eventCount": agg["events"],
                "productionCount": random.randint(1000, 5000),
                "stageStatus": {
                    "WEG": agg["stage"].get("WEG", "PENDING"),
                    "FBD": agg["stage"].get("FBD", "PENDING"),
                    "BLE": agg["stage"].get("BLE", "PENDING"),
                },
                "createdAt": now_utc(),
                "updatedAt": now_utc(),
                "hierarchy": {
                    "plant": PLANT_ID,
                    "block": BLOCK_ID,
                    "area": AREA_ID,
                    "room": ROOM_ID,
                    "fullPath": f"{PLANT_ID}/{BLOCK_ID}/{AREA_ID}/{ROOM_ID}/PIPELINE",
                },
            }
        )

    upsert_many(db, "iiot_ingestion_checkpoint", checkpoint_docs, "checkpointId")
    upsert_many(db, "iiot_ingestion_job_run", job_docs, "jobRunId")
    upsert_many(db, "iiot_equipment_live_status", live_status_docs, "equipmentId")

    if batch_summaries:
        db.iiot_batch_summary.insert_many(batch_summaries, ordered=False)

    return {
        "cpp": cpp_total,
        "alarms": 0,
        "events": 0,
        "batchSummary": len(batch_summaries),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Load IIOT schema-compatible data from realtime Excel sheets")
    parser.add_argument("--mongo-uri", default=DEFAULT_MONGO_URI, help="MongoDB connection URI")
    parser.add_argument("--db", default=DB_NAME, help="MongoDB database name")
    parser.add_argument(
        "--excel-dir",
        default=str(Path(__file__).resolve().parents[1] / "realtime_sample_data"),
        help="Directory containing WEG.xlsx, FBD.xlsx, BLE (1).xlsx",
    )
    parser.add_argument("--no-reset", action="store_true", help="Do not clear existing IIOT collections before loading")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    excel_dir = Path(args.excel_dir)
    if not excel_dir.exists():
        print(f"[IIOT-LOAD] ERROR: excel directory not found: {excel_dir}")
        return 1

    eq_defs = equipment_list()
    eq_by_type = {eq.equipment_type: eq for eq in eq_defs}

    records, parameter_keys_by_type = load_excel_records(excel_dir)
    if not records:
        print("[IIOT-LOAD] ERROR: no realtime records parsed from Excel files")
        return 1

    client = MongoClient(args.mongo_uri)
    db = client[args.db]

    maybe_reset_collections(db, eq_defs, reset=not args.no_reset)
    create_indexes(db)

    equipment_docs, source_docs = build_master_data(eq_defs)
    products = build_products(records)

    upsert_many(db, "iiot_equiment_master", equipment_docs, "equipmentId")
    upsert_many(db, "iiot_product_master", products, "productId")
    upsert_many(db, "iiot_source_table_mapping", source_docs, "mappingId")

    param_docs, limit_docs = build_parameter_and_limit_docs(records, eq_by_type, parameter_keys_by_type)
    upsert_many_composite(db, "iiot_equipment_critical_parameters", param_docs, ["equipmentId", "parameterId"])
    upsert_many_composite(db, "iiot_equipment_critical_parameters_limit", limit_docs, ["equipmentId", "parameterLimitId"])

    product_by_code = {p["productCode"]: p for p in products}

    # Build quick lookup: (equipmentId, metricKey) -> limits
    limit_lookup: dict[tuple[str, str], dict[str, Any]] = {}
    for l in limit_docs:
        metric_key = l.get("parameterKey") or l["parameterId"].rsplit("_", 1)[0]
        limit_lookup[(l["equipmentId"], metric_key)] = l

    stats = load_realtime_data(db, records, eq_by_type, product_by_code, limit_lookup)

    print("[IIOT-LOAD] Completed successfully")
    print(f"[IIOT-LOAD] Records parsed: {len(records)}")
    print(f"[IIOT-LOAD] Products upserted: {len(products)}")
    print(f"[IIOT-LOAD] Parameters upserted: {len(param_docs)}")
    print(f"[IIOT-LOAD] Parameter limits upserted: {len(limit_docs)}")
    print(f"[IIOT-LOAD] CPP inserted: {stats['cpp']}")
    print(f"[IIOT-LOAD] Fallback alarms inserted: {stats['alarms']}")
    print(f"[IIOT-LOAD] Fallback events inserted: {stats['events']}")
    print(f"[IIOT-LOAD] Batch summaries inserted: {stats['batchSummary']}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
