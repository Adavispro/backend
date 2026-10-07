#!/usr/bin/env python3
"""Mock data service that mimics the source API used by the scheduler.

Matches endpoints defined in:
- Production fwxapi endpoint contract:
  GET /fwxapi/rest/v1/Dataset?pointName=db:HMI.<dataset_name><@AssetId='...', ...>
  GET /Dataset?pointName=db:HMI.<dataset_name><@AssetId='...', ...>

Supports the 5 target equipments:
1. RMG (MB003, Asset ID 10094)
2. FBD (MB004, Asset ID 10110)
3. Blender / BLE (MB005, Asset ID 10095)
4. Compression / COMP (MB040, Asset ID 10040)
5. Coating / COAT (MB041, Asset ID 10141)

Exposes continuous real-time streaming telemetry and reference datasets:
- Common datasets: Batch_Info, Batch_summary, Mach_summary, Login, Users, Alarms, Audit_Trail
- Equipment-specific datasets:
  * RMG_op_data, RMG_recipe
  * FBD_Op_Data, FBD_Recipe, FBD_Min_Max
  * BLE_Op_Data (Blend_Op_Data), BLE_Recipe (Blend_Recipe), BLE_Min_Max
  * COAT_Op_Data (Coat_Op_Data), COAT_Recipe (Coat_Recipe), COAT_Min_Max
  * COMP_Op_Data, COMP_Recipe, COMP_Min_Max
- Legacy point name compatibility: BATCHDETAILS, BATCHDATA, ALARMDATA, AUDITDATA, PARAMETERSETTINGS
"""

from __future__ import annotations

import json
import math
import os
import random
import sys
import time
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, urlparse

HOST = "0.0.0.0"
PORT = 8000

EXTRACTED_DATA_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "extracted_reference_data.json",
)

REFERENCE_DOC_DATA: dict[str, dict] = {}
if os.path.exists(EXTRACTED_DATA_PATH):
    try:
        with open(EXTRACTED_DATA_PATH, "r", encoding="utf-8") as f:
            REFERENCE_DOC_DATA = json.load(f)
    except Exception as e:
        print(f"Warning: Failed to load {EXTRACTED_DATA_PATH}: {e}", file=sys.stderr)

# Asset ID <-> Equipment mapping
ASSET_TO_EQUIPMENT = {
    "10094": "MB003",
    "10110": "MB004",
    "10095": "MB005",
    "10040": "MB040",
    "10141": "MB041",
    # Alternative IDs from docx
    "10012": "MB005",
    "10021": "MB041",
}

EQUIPMENT_TO_ASSET = {
    "MB003": "10094",
    "MB004": "10110",
    "MB005": "10095",
    "MB040": "10040",
    "MB041": "10141",
    "G5RMG": "10094",
    "G5FBD": "10110",
    "G5OGB": "10095",
    "G5BLE": "10095",
    "G5COAT": "10141",
}

EQUIPMENT_CONFIG = {
    "MB003": {
        "equipmentId": "MB003",
        "equipmentCode": "MB003",
        "assetId": "10094",
        "equipmentType": "RMG",
        "equipmentName": "Rapid mixer Granulator",
        "make": "BECTOCHEM",
        "model": "MITSUBISHI FX5U 32 MR",
        "plc": "MITSUBISHI FX5U 32 MR",
        "block": "PB1",
        "area": "MODULE-B",
        "batchNo": "AGO0026016",
        "lotNo": "01",
        "productName": "LAMOTRIGINE",
        "productCode": "STGW2000",
        "recipeName": "AGO",
        "batchSize": 248.640,
        "operator": "96828 (PB1-RMG (MB003) Operator)",
        "operatorId": "96828",
        "supervisor": "96365 (PB1-RMG (MB003) Supervisor)",
        "supervisorId": "96365",
    },
    "MB004": {
        "equipmentId": "MB004",
        "equipmentCode": "MB004",
        "assetId": "10110",
        "equipmentType": "FBD",
        "equipmentName": "Fluid bed drier",
        "make": "ALLIANCE",
        "model": "MITSUBISHI Fx5U 32 MR",
        "plc": "MITSUBISHI Fx5U 32 MR",
        "block": "PB1",
        "area": "MODULE-B",
        "batchNo": "AGO0026016",
        "lotNo": "1B",
        "productName": "LAMOTRIGINE",
        "productCode": "STGW2000",
        "recipeName": "AGO",
        "batchSize": 248.640,
        "operator": "11173 (PB1-Module-B (MB004) Operator)",
        "operatorId": "11173",
        "supervisor": "191555 (PB1-Module-B (MB004) Supervisor)",
        "supervisorId": "191555",
    },
    "MB005": {
        "equipmentId": "MB005",
        "equipmentCode": "MB005",
        "assetId": "10095",
        "equipmentType": "BLE",
        "equipmentName": "Blender",
        "make": "BECTOCHEM",
        "model": "MITSUBISHI Fx3U 32 MR",
        "plc": "MITSUBISHI Fx3U 32 MR",
        "block": "PB1",
        "area": "MODULE-B",
        "batchNo": "AGO0026015",
        "lotNo": "01",
        "productName": "LAMOTRIGINE",
        "productCode": "STGW2000",
        "recipeName": "AGO0026015",
        "batchSize": 248.640,
        "operator": "11173 (PB1-Module-B-Blender-Operator)",
        "operatorId": "11173",
        "supervisor": "191164 (Harish Chandra Mishra-191164(PB1-Module-B-Blender-Supervisor))",
        "supervisorId": "191164",
    },
    "MB040": {
        "equipmentId": "MB040",
        "equipmentCode": "MB040",
        "assetId": "10040",
        "equipmentType": "COMP",
        "equipmentName": "Compression machine",
        "make": "SEJONG",
        "model": "OMRON Sysmac CJ1G CPU 44H",
        "plc": "OMRON Sysmac CJ1G CPU 44H",
        "block": "PB1",
        "area": "MODULE-B",
        "batchNo": "ADNC26011",
        "lotNo": "01",
        "productName": "Amisulpride 200mg",
        "productCode": "STFS7000",
        "recipeName": "SEJONG-49D",
        "batchSize": 500000.0,
        "operator": "10401 (PB1-Compression-Operator)",
        "operatorId": "10401",
        "supervisor": "10402 (PB1-Compression-Supervisor)",
        "supervisorId": "10402",
    },
    "MB041": {
        "equipmentId": "MB041",
        "equipmentCode": "MB041",
        "assetId": "10141",
        "equipmentType": "COAT",
        "equipmentName": "Coating machine",
        "make": "GANSONS",
        "model": "MITSUBISHI Fx3U 32 MR",
        "plc": "MITSUBISHI Fx3U 32 MR",
        "block": "PB1",
        "area": "COATING MODULE-B",
        "batchNo": "PED26009",
        "lotNo": "01",
        "productName": "PAROXETINE USP 40mg",
        "productCode": "STPA1D00",
        "recipeName": "PAROXE40",
        "batchSize": 625000.0,
        "operator": "29995 (PB1-Module-B-Operator)",
        "operatorId": "29995",
        "supervisor": "191257 (PB1-Module-B-Supervisor)",
        "supervisorId": "191257",
    },
}

# Legacy aliases
EQUIPMENT_CONFIG["G5RMG"] = EQUIPMENT_CONFIG["MB003"]
EQUIPMENT_CONFIG["G5FBD"] = EQUIPMENT_CONFIG["MB004"]
EQUIPMENT_CONFIG["G5OGB"] = EQUIPMENT_CONFIG["MB005"]
EQUIPMENT_CONFIG["G5BLE"] = EQUIPMENT_CONFIG["MB005"]
EQUIPMENT_CONFIG["G5COAT"] = EQUIPMENT_CONFIG["MB041"]


def resolve_equipment_id(dataset_id: str, params: dict[str, str], dataset_name: str = "") -> str:
    """Resolve standard equipment code (MB003, MB004, MB005, MB040, MB041) from query info."""
    asset_id = str(params.get("AssetId") or params.get("assetid") or params.get("asset_id") or "").strip()
    if asset_id in ASSET_TO_EQUIPMENT:
        return ASSET_TO_EQUIPMENT[asset_id]

    did = str(dataset_id or "").strip().upper()
    if did in EQUIPMENT_CONFIG:
        return did
    if "MB003" in did or "RMG" in did:
        return "MB003"
    if "MB004" in did or "FBD" in did:
        return "MB004"
    if "MB005" in did or "BLE" in did or "OGB" in did:
        return "MB005"
    if "MB040" in did or "COMP" in did:
        return "MB040"
    if "MB041" in did or "COAT" in did or "COT" in did:
        return "MB041"

    dname = str(dataset_name or "").upper()
    if "RMG" in dname:
        return "MB003"
    if "FBD" in dname:
        return "MB004"
    if "BLE" in dname or "BLEND" in dname:
        return "MB005"
    if "COMP" in dname:
        return "MB040"
    if "COAT" in dname:
        return "MB041"

    return "MB003"


def dataset_family(equipment_id: str) -> str:
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    return cfg["equipmentType"]


def parse_pointname(pointname: str):
    """Return dataset_id, dataset_name, and normalized parameter map."""
    name = pointname.strip()
    params = {}
    dataset_id = "MB003"

    if "<" in name and ">" in name:
        left = name.index("<") + 1
        right = name.rindex(">")
        inner = name[left:right]
        name = name[: name.index("<")].strip()
        for part in inner.split(","):
            if "=" not in part:
                continue
            key, value = [item.strip() for item in part.split("=", 1)]
            clean_key = key.lstrip("@").strip()
            clean_val = value.strip("'\"")
            params[clean_key] = clean_val
            params[clean_key.lower()] = clean_val
            params[clean_key.replace("_", "").lower()] = clean_val

    if "." in name:
        parts = name.split(".", 1)
        dataset_id = parts[0].replace("db:", "").strip()
        dataset_name = parts[1].strip()
    else:
        dataset_name = name.replace("db:", "").strip()

    eq_code = resolve_equipment_id(dataset_id, params, dataset_name)
    return eq_code, dataset_name, params


def _to_iso(dt_str: str) -> str:
    s = str(dt_str or "").strip()
    if not s:
        return datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    if "T" in s:
        return s
    if "/" in s:
        parts = s.split()
        date_parts = parts[0].split("/")
        time_part = parts[1] if len(parts) > 1 else "00:00:00"
        if len(date_parts) == 3:
            day, month, year = date_parts
            return f"{year}-{month.zfill(2)}-{day.zfill(2)}T{time_part}Z"
    return s


def build_batch_info(equipment_id: str = "MB003") -> list[dict[str, Any]]:
    """Return run / completed batch list matching db:HMI.Batch_Info."""
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    now = datetime.now()
    start_dt = (now - timedelta(hours=3)).strftime("%d/%m/%Y %H:%M:%S")
    end_dt = now.strftime("%d/%m/%Y %H:%M:%S")

    items = [
        {
            "BatchNo": cfg["batchNo"],
            "LotNo": cfg["lotNo"],
            "ProductNo": cfg["productCode"],
            "BatchStartDate": start_dt,
            "BatchEndDate": end_dt,
        }
    ]
    # Add historical lots if available
    if cfg["equipmentType"] == "FBD":
        items.insert(0, {
            "BatchNo": cfg["batchNo"],
            "LotNo": "1A",
            "ProductNo": cfg["productCode"],
            "BatchStartDate": (now - timedelta(hours=6)).strftime("%d/%m/%Y %H:%M:%S"),
            "BatchEndDate": (now - timedelta(hours=3)).strftime("%d/%m/%Y %H:%M:%S"),
        })
    return items


def build_batch_details(equipment_id: str) -> list[dict[str, Any]]:
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    return [
        {
            "PRODUCT_NAME": cfg["productName"],
            "PRODUCT_CODE": cfg["productCode"],
            "RECIPE_NAME": cfg["recipeName"],
            "BATCH_NO": cfg["batchNo"],
            "LOT_NO": cfg["lotNo"],
            "BATCH_SIZE_KG": cfg["batchSize"],
            "product_name": cfg["productName"],
            "product_code": cfg["productCode"],
            "recipe_name": cfg["recipeName"],
            "batch_no": cfg["batchNo"],
            "lot_no": cfg["lotNo"],
            "batch_size_kg": cfg["batchSize"],
        }
    ]


def build_batch_summary(equipment_id: str) -> list[dict[str, Any]]:
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    now = datetime.now()
    start_time = (now - timedelta(hours=2, minutes=30)).strftime("%d/%m/%Y %H:%M:%S")
    end_time = now.strftime("%d/%m/%Y %H:%M:%S")

    return [
        {
            "Batch Number": cfg["batchNo"],
            "Lot Number": cfg["lotNo"],
            "Product Name": cfg["productName"],
            "Recipe Name": cfg["recipeName"],
            "Batch Size": f"{cfg['batchSize']:.3f} {'Kg' if cfg['equipmentType'] != 'COAT' and cfg['equipmentType'] != 'COMP' else 'Tabs'}",
            "Start Time": start_time,
            "End Time": end_time,
            "Batch Duration in Hours": "02:30:00",
            # Standard keys
            "batchNo": cfg["batchNo"],
            "lotNo": cfg["lotNo"],
            "productName": cfg["productName"],
            "productCode": cfg["productCode"],
        }
    ]


def build_mach_summary(equipment_id: str) -> list[dict[str, Any]]:
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    return [
        {
            "Equipment Name": cfg["equipmentName"],
            "Equipment ID": cfg["equipmentId"],
            "Make": cfg["make"],
            "Block": cfg["block"],
            "Area": cfg["area"],
            "PLC": cfg["plc"],
            "AssetId": cfg["assetId"],
        }
    ]


def build_login_users(equipment_id: str) -> list[dict[str, Any]]:
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    now = datetime.now()
    t1 = (now - timedelta(hours=2, minutes=30)).strftime("%d/%m/%Y %H:%M:%S")
    t2 = (now - timedelta(hours=2, minutes=20)).strftime("%d/%m/%Y %H:%M:%S")
    t3 = (now - timedelta(minutes=15)).strftime("%d/%m/%Y %H:%M:%S")
    t4 = now.strftime("%d/%m/%Y %H:%M:%S")

    return [
        {
            "User Name": cfg["supervisor"],
            "Date And Time": t1,
            "Description": "Logout Sucessfully",
        },
        {
            "User Name": cfg["operator"],
            "Date And Time": t2,
            "Description": "Login",
        },
        {
            "User Name": cfg["operator"],
            "Date And Time": t3,
            "Description": "Logout Sucessfully",
        },
        {
            "User Name": cfg["supervisor"],
            "Date And Time": t4,
            "Description": "Login",
        },
    ]


def build_audit_trail(equipment_id: str) -> list[dict[str, Any]]:
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    now = datetime.now()
    t1 = (now - timedelta(hours=2, minutes=25)).strftime("%d/%m/%Y %H:%M:%S")
    t2 = (now - timedelta(hours=1, minutes=45)).strftime("%d/%m/%Y %H:%M:%S")
    t3 = (now - timedelta(minutes=30)).strftime("%d/%m/%Y %H:%M:%S")

    return [
        {
            "Date Time": t1,
            "DateTime": t1,
            "DT": t1,
            "Description": "Auto Mode Selected",
            "Old Value": "Manual",
            "New Value": "Auto",
            "Reason": "Batch Initialized",
            "User Name": cfg["operator"],
            "RecordID": f"AUD-{cfg['equipmentType']}-01",
        },
        {
            "Date Time": t2,
            "DateTime": t2,
            "DT": t2,
            "Description": f"Recipe Loaded: {cfg['recipeName']}",
            "Old Value": "-",
            "New Value": cfg["recipeName"],
            "Reason": "Standard Processing",
            "User Name": cfg["operator"],
            "RecordID": f"AUD-{cfg['equipmentType']}-02",
        },
        {
            "Date Time": t3,
            "DateTime": t3,
            "DT": t3,
            "Description": "Cycle In Progress Verification",
            "Old Value": "Running",
            "New Value": "Verified",
            "Reason": "Quality In-Process Check",
            "User Name": cfg["supervisor"],
            "RecordID": f"AUD-{cfg['equipmentType']}-03",
        },
    ]


def build_alarm_data(equipment_id: str, from_time: str = "", to_time: str = "") -> list[dict[str, Any]]:
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    eq_type = cfg["equipmentType"]

    now = datetime.now()
    occ_str = (now - timedelta(minutes=18)).strftime("%d/%m/%Y %H:%M:%S")
    res_str = (now - timedelta(minutes=15)).strftime("%d/%m/%Y %H:%M:%S")

    alarm_catalog = {
        "RMG": ("DISCHARGE VALVE CLOSE FAIL", "ALM-101", 101),
        "FBD": ("PC AIR PRESSURE LOW", "ALM-201", 201),
        "BLE": ("BLENDER GUARD OPEN", "ALM-401", 401),
        "COAT": ("INLET AIR TEMP HIGH", "ALM-301", 301),
        "COMP": ("Low Air Pressure", "ALM-501", 501),
    }

    alarm_name, alarm_code, msg_num = alarm_catalog.get(eq_type, ("GENERAL PROCESS WARNING", "ALM-001", 1))

    return [
        {
            "Time_ms": time.time() * 1000,
            "MsgProc": 1,
            "StateAfter": 0,
            "MsgClass": 2,
            "MsgNumber": msg_num,
            "Var1": "Process Error",
            "Var2": cfg["equipmentId"],
            "Var3": "",
            "Var4": "",
            "Var5": "",
            "Var6": "",
            "Var7": "",
            "Var8": "",
            "TimeString": "00:03:00",
            "MsgText": alarm_name,
            "Alarm_Name": alarm_name,
            "PLC": cfg["plc"],
            "DT": occ_str,
            "DateTime": occ_str,
            "Occurred_Time": occ_str,
            "Resolved_Time": res_str,
            "Duration": "00:03:00",
            "EquipmentCode": cfg["equipmentId"],
            "EquipmentType": eq_type,
            "severity": "WARNING",
            "alarmCode": alarm_code,
        }
    ]


def build_parameter_settings(equipment_id: str, batch_no: str = "", lot_no: str = "") -> Any:
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    eq_type = cfg["equipmentType"]

    if eq_type == "RMG":
        return [
            {"Parameter": "<b>DRY CYCLE 1", "Value": ""},
            {"Parameter": "DRY CYCLE1 IMPELLER SLOW SET (Sec)", "Value": "600"},
            {"Parameter": "DRY CYCLE1 IMPELLER FAST SET (Sec)", "Value": "0"},
            {"Parameter": "DRY CYCLE1 CHOPPER DELAY (Sec)", "Value": "0"},
            {"Parameter": "DRY CYCLE1 CHOPPER SLOW SET (Sec)", "Value": "0"},
            {"Parameter": "DRY CYCLE1 CHOPPER FAST SET (Sec)", "Value": "0"},
            {"Parameter": "<b>WET CYCLE 1", "Value": ""},
            {"Parameter": "WET CYCLE1 IMPELLER SLOW SET (Sec)", "Value": "150"},
            {"Parameter": "WET CYCLE1 IMPELLER FAST SET (Sec)", "Value": "0"},
            {"Parameter": "WET CYCLE1 CHOPPER DELAY (Sec)", "Value": "0"},
            {"Parameter": "WET CYCLE1 CHOPPER SLOW SET (Sec)", "Value": "0"},
            {"Parameter": "WET CYCLE1 CHOPPER FAST SET (Sec)", "Value": "0"},
            {"Parameter": "WET CYCLE1 PUMP1 ON DELAY (Sec)", "Value": "0"},
            {"Parameter": "WET CYCLE1 PUMP1 SET (Sec)", "Value": "150"},
            {"Parameter": "WET CYCLE1 PUMP1 RPM", "Value": "60"},
            {"Parameter": "<b>WET CYCLE 2", "Value": ""},
            {"Parameter": "WET CYCLE2 IMPELLER SLOW SET (Sec)", "Value": "60"},
            {"Parameter": "WET CYCLE2 CHOPPER SLOW SET (Sec)", "Value": "60"},
            {"Parameter": "<b>WET CYCLE 3", "Value": ""},
            {"Parameter": "WET CYCLE3 IMPELLER FAST SET (Sec)", "Value": "30"},
            {"Parameter": "WET CYCLE3 CHOPPER FAST SET (Sec)", "Value": "30"},
            {"Parameter": "<b>UN LOADING PARAMETERS", "Value": ""},
            {"Parameter": "IMPELLER", "Value": "SLOW"},
            {"Parameter": "CHOPPER", "Value": "SLOW"},
        ]
    elif eq_type == "FBD":
        return [
            {"Parameter": "PROCESS TIME (MIN)", "value": "500"},
            {"Parameter": "AIR DRY TIME (MIN)", "value": "5"},
            {"Parameter": "COOLING TIME (MIN)", "value": "0"},
            {"Parameter": "SHAKE INTERVAL (MIN)", "value": "10"},
            {"Parameter": "SHAKE DURATION (SEC)", "value": "30"},
            {"Parameter": "END SHAKE TIME (SEC)", "value": "60"},
            {"Parameter": "INLET TEMPERATURE (°C)", "value": "60"},
            {"Parameter": "EXHAUST TEMPERATURE (°C)", "value": "50"},
            {"Parameter": "INLET ALARM TEMPERATURE (°C)", "value": "65"},
            {"Parameter": "PRINT INTERVAL (MIN)", "value": "5"},
        ]
    elif eq_type == "BLE":
        return [
            {"Parameter": "SELECT NUMBER OF MIXINGS", "Value": "2"},
            {"Parameter": "FIRST MIXING TIME (MIN)", "Value": "10"},
            {"Parameter": "SECOND MIXING TIME (MIN)", "Value": "5"},
            {"Parameter": "THIRD MIXING TIME (MIN)", "Value": "0"},
            {"Parameter": "FOURTH MIXING TIME (MIN)", "Value": "0"},
            {"Parameter": "BLENDING SPEED (RPM)", "Value": "5"},
            {"Parameter": "VACUUM ON TIME (MIN)", "Value": "1"},
            {"Parameter": "PURGE ON TIME (Sec)", "Value": "0"},
        ]
    elif eq_type == "COAT":
        return [
            {"Parameter": "INLET AIR TEMP SET (C)", "Value": "60"},
            {"Parameter": "BED TEMP SET (C)", "Value": "44"},
            {"Parameter": "PAN SPEED SET (RPM)", "Value": "2.2"},
            {"Parameter": "SPRAY RATE SET (G/MIN)", "Value": "120"},
            {"Parameter": "ATOMIZING AIR PRESSURE (BAR)", "Value": "2.5"},
            {"Parameter": "PATTERN AIR PRESSURE (BAR)", "Value": "2.0"},
            {"Parameter": "PROCESS TIME (MIN)", "Value": "450"},
        ]
    else:  # COMP
        return [
            {"Parameter": "TURRET SPEED SET (RPM)", "Value": "30"},
            {"Parameter": "FEEDER SPEED SET (RPM)", "Value": "13"},
            {"Parameter": "TARGET QUANTITY (TABS)", "Value": "500000"},
            {"Parameter": "MAIN COMPRESSION FORCE SET (KN)", "Value": "19.5"},
            {"Parameter": "PRE COMPRESSION FORCE SET (KN)", "Value": "2.3"},
            {"Parameter": "AIR PRESSURE LOW LIMIT (KPA)", "Value": "400"},
            {"Parameter": "HYDRAULIC PRESSURE (MPA)", "Value": "7.5"},
        ]


def build_min_max(equipment_id: str) -> list[dict[str, Any]]:
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    eq_type = cfg["equipmentType"]

    if eq_type == "FBD":
        return [
            {"Parameter": "INLET TEMPARATURE (°C)", "MIN": "26", "MAX": "61"},
            {"Parameter": "EXHAUST TEMPARATURE (°C)", "MIN": "20", "MAX": "49"},
        ]
    elif eq_type == "COAT":
        return [
            {"Parameter": "INLET TEMPARATURE (°C)", "MIN": "29.0", "MAX": "63.4"},
            {"Parameter": "BED TEMPARATURE (°C)", "MIN": "23.6", "MAX": "50.5"},
            {"Parameter": "PAN SPEED (RPM)", "MIN": "2.0", "MAX": "2.2"},
            {"Parameter": "DOSING SPEED (RPM)", "MIN": "13.6", "MAX": "15.6"},
        ]
    elif eq_type == "COMP":
        return [
            {"Parameter": "TURRET SPEED (RPM)", "MIN": "25", "MAX": "40"},
            {"Parameter": "MAIN COMPRESSION FORCE (KN)", "MIN": "15.0", "MAX": "22.0"},
            {"Parameter": "PRE COMPRESSION FORCE (KN)", "MIN": "1.5", "MAX": "3.0"},
        ]
    elif eq_type == "BLE":
        return [
            {"Parameter": "BLENDING SPEED (RPM)", "MIN": "4.5", "MAX": "5.5"},
        ]
    else:  # RMG
        return [
            {"Parameter": "IMPELLER CURRENT (A)", "MIN": "18.0", "MAX": "26.7"},
            {"Parameter": "CHOPPER CURRENT (A)", "MIN": "3.5", "MAX": "6.2"},
            {"Parameter": "PUMP RPM", "MIN": "55", "MAX": "65"},
        ]


def build_streaming_telemetry(equipment_id: str, count: int = 10) -> list[dict[str, Any]]:
    """Generate continuous advancing telemetry for the equipment with realistic fluctuations."""
    cfg = EQUIPMENT_CONFIG.get(equipment_id, EQUIPMENT_CONFIG["MB003"])
    eq_type = cfg["equipmentType"]
    now = datetime.now()
    step_seconds = 10

    records: list[dict[str, Any]] = []
    # Generate advancing points leading up to current second
    for i in range(count - 1, -1, -1):
        point_time = now - timedelta(seconds=i * step_seconds)
        time_str = point_time.strftime("%d/%m/%Y %H:%M:%S")
        iso_str = point_time.strftime("%Y-%m-%dT%H:%M:%SZ")
        t_phase = (point_time.minute * 60 + point_time.second) / 30.0

        if eq_type == "RMG":
            # Impeller Amp (18.0A - 26.7A), Chopper Amp (3.5A - 6.2A), Pump RPM (60 RPM)
            imp_amp = round(22.35 + 4.35 * math.sin(t_phase), 2)
            chp_amp = round(4.85 + 1.35 * math.cos(t_phase * 1.5), 2)
            pump_rpm = 60.0
            ag_spd = 140.0
            chp_spd = 1420.0
            temp = round(52.0 + 3.0 * math.sin(t_phase * 0.5), 1)

            rec = {
                "TIME": time_str,
                "DT": time_str,
                "timestamp": iso_str,
                "observedAt": iso_str,
                "STATUS": "RUNNING",
                "CURRENT (Amp)": imp_amp,
                "IMPELLER CURRENT (Amp)": imp_amp,
                "CHOPPER CURRENT (Amp)": chp_amp,
                "agAmps": imp_amp,
                "chpAmps": chp_amp,
                "agSpeed": ag_spd,
                "chpSpeed": chp_spd,
                "pumpRpm": pump_rpm,
                "heaterTemp": temp,
                "DURATION (SEC)": 180 + (count - i) * step_seconds,
                "batch_no": cfg["batchNo"],
                "lot_no": cfg["lotNo"],
                "status": "RUNNING",
                "user_name": cfg["operator"],
                "equipmentCode": cfg["equipmentId"],
                "equipmentType": eq_type,
            }

        elif eq_type == "FBD":
            # Inlet Temp (26°C - 61°C), Exhaust Temp (20°C - 49°C), Shaking cycle state
            inlet_temp = round(43.5 + 17.5 * math.sin(t_phase * 0.8), 1)
            # Clamp in range [26, 61]
            inlet_temp = max(26.0, min(61.0, inlet_temp))
            exhaust_temp = round(34.5 + 14.5 * math.cos(t_phase * 0.8), 1)
            exhaust_temp = max(20.0, min(49.0, exhaust_temp))
            shake_state = "RUNNING" if (point_time.second % 60) < 40 else "SHAKING"

            rec = {
                "TIME": time_str,
                "DT": time_str,
                "timestamp": iso_str,
                "observedAt": iso_str,
                "INLET TEMPARATURE": inlet_temp,
                "EXHAUST TEMPARATURE": exhaust_temp,
                "inletTemp": inlet_temp,
                "outletTemp": exhaust_temp,
                "shakeCycleState": shake_state,
                "STATUS": "RUNNING",
                "batch_no": cfg["batchNo"],
                "lot_no": cfg["lotNo"],
                "status": "RUNNING",
                "user_name": cfg["operator"],
                "equipmentCode": cfg["equipmentId"],
                "equipmentType": eq_type,
            }

        elif eq_type == "BLE":
            # Blender Speed (5.0 RPM), Mixing timer countdown, Vacuum status
            speed = round(5.0 + 0.15 * math.sin(t_phase), 2)
            timer_min = max(0, 15 - (point_time.minute % 15))
            vacuum = "ON"

            rec = {
                "TIME": time_str,
                "DT": time_str,
                "timestamp": iso_str,
                "observedAt": iso_str,
                "BLENDER SPEED (RPM)": speed,
                "actualRpm": speed,
                "mixingTimerMinutes": timer_min,
                "vacuumStatus": vacuum,
                "STATUS": "RUNNING",
                "batch_no": cfg["batchNo"],
                "lot_no": cfg["lotNo"],
                "status": "RUNNING",
                "user_name": cfg["operator"],
                "equipmentCode": cfg["equipmentId"],
                "equipmentType": eq_type,
            }

        elif eq_type == "COAT":
            # Inlet Temp (29°C - 63.4°C), Bed Temp (23.6°C - 50.5°C), Pan Speed (2.0 - 2.2 RPM), Dosing Speed (13.6 - 15.6 RPM)
            inlet = round(46.2 + 17.2 * math.sin(t_phase), 1)
            inlet = max(29.0, min(63.4, inlet))
            bed = round(37.05 + 13.45 * math.cos(t_phase), 1)
            bed = max(23.6, min(50.5, bed))
            pan = round(2.1 + 0.1 * math.sin(t_phase * 2), 2)
            pan = max(2.0, min(2.2, pan))
            dosing = round(14.6 + 1.0 * math.cos(t_phase * 1.5), 1)
            dosing = max(13.6, min(15.6, dosing))

            rec = {
                "TIME": time_str,
                "DT": time_str,
                "timestamp": iso_str,
                "observedAt": iso_str,
                "INLET TEMPARATURE": inlet,
                "BED TEMPARATURE": bed,
                "PAN SPEED": pan,
                "DOSING SPEED": dosing,
                "inletAirTemp": inlet,
                "bedTemp": bed,
                "panSpeed": pan,
                "sprayRate": dosing,
                "atomAirPress": 2.5,
                "STATUS": "RUNNING",
                "batch_no": cfg["batchNo"],
                "lot_no": cfg["lotNo"],
                "status": "RUNNING",
                "user_name": cfg["operator"],
                "equipmentCode": cfg["equipmentId"],
                "equipmentType": eq_type,
            }

        else:  # COMP
            # Turret RPM (25 - 40 RPM), Main Compression Force (kN), Pre-Force (kN), Tablet Count
            turret = round(32.5 + 7.5 * math.sin(t_phase), 1)
            turret = max(25.0, min(40.0, turret))
            main_force = round(18.5 + 2.5 * math.cos(t_phase), 2)
            pre_force = round(2.25 + 0.65 * math.sin(t_phase * 1.5), 2)
            base_count = 125000 + int((point_time.minute * 60 + point_time.second) * 25)

            rec = {
                "TIME": time_str,
                "DT": time_str,
                "timestamp": iso_str,
                "observedAt": iso_str,
                "TURRET RPM": turret,
                "turretRpm": turret,
                "diskSpeed": turret,
                "MAIN COMPRESSION FORCE (kN)": main_force,
                "mainCompForce": main_force,
                "PRE COMPRESSION FORCE (kN)": pre_force,
                "preCompForce": pre_force,
                "TABLET COUNT": base_count,
                "tabletCount": base_count,
                "feederSpeed": 13.0,
                "STATUS": "RUNNING",
                "batch_no": cfg["batchNo"],
                "lot_no": cfg["lotNo"],
                "status": "RUNNING",
                "user_name": cfg["operator"],
                "equipmentCode": cfg["equipmentId"],
                "equipmentType": eq_type,
            }

        records.append(rec)

    return records


class MockDataServiceHandler(BaseHTTPRequestHandler):
    """Handles REST GET queries for dataset point names matching live fwxapi."""

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path

        # Support both standard production fwxapi path and interim /Dataset path
        if not (path.startswith("/fwxapi/rest/v1/Dataset") or path.startswith("/Dataset") or path == "/health"):
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'{"error": "Not Found"}')
            return

        if path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status": "healthy"}')
            return

        query_params = parse_qs(parsed.query)
        pointname = (
            query_params.get("pointname", [""])[0]
            or query_params.get("pointName", [""])[0]
            or query_params.get("PointName", [""])[0]
        )

        if not pointname:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b'{"error": "pointName query parameter is required"}')
            return

        eq_code, dataset_name, params = parse_pointname(pointname)
        dname_upper = dataset_name.upper().replace("_", "")

        payload: Any = []

        # Route pointName to appropriate handler
        if dname_upper in ("BATCHINFO", "BATCHDETAILS"):
            payload = build_batch_info(eq_code)
        elif dname_upper == "BATCHSUMMARY":
            payload = build_batch_summary(eq_code)
        elif dname_upper == "MACHSUMMARY":
            payload = build_mach_summary(eq_code)
        elif dname_upper in ("LOGIN", "USERS"):
            payload = build_login_users(eq_code)
        elif dname_upper in ("ALARMS", "ALARMDATA"):
            payload = build_alarm_data(eq_code, params.get("fromtime", ""), params.get("totime", ""))
        elif dname_upper in ("AUDITTRAIL", "AUDITDATA"):
            payload = build_audit_trail(eq_code)
        elif "RECIPE" in dname_upper or dname_upper == "PARAMETERSETTINGS":
            payload = build_parameter_settings(
                eq_code,
                params.get("batchno", "AGO0026016"),
                params.get("lotno", "01"),
            )
        elif "MINMAX" in dname_upper:
            payload = build_min_max(eq_code)
        elif "OPDATA" in dname_upper or dname_upper == "BATCHDATA":
            payload = build_streaming_telemetry(eq_code, count=12)
        else:
            # Fallback to streaming telemetry
            payload = build_streaming_telemetry(eq_code, count=12)

        cfg = EQUIPMENT_CONFIG.get(eq_code, EQUIPMENT_CONFIG["MB003"])
        response_payload = {
            "status": "success",
            "pointname": pointname,
            "dataset_id": eq_code,
            "asset_id": cfg["assetId"],
            "dataset": dataset_name,
            "data": payload,
        }

        body = json.dumps(response_payload, default=str).encode("utf-8")

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return


def run_server(host=HOST, port=PORT):
    server = ThreadingHTTPServer((host, port), MockDataServiceHandler)
    print(f"Mock Data Service streaming active on http://{host}:{port}", flush=True)
    server.serve_forever()


def main():
    port = PORT
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError as exc:
            raise SystemExit(f"Invalid port value: {sys.argv[1]}") from exc

    run_server(HOST, port)


if __name__ == "__main__":
    main()
