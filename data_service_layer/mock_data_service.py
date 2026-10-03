#!/usr/bin/env python3
"""Mock data service that mimics the source API used by the scheduler.

Supports:
- PointName datasets matching live production fwxapi endpoint contract:
  * GET /Dataset?pointName=db:HMI.<dataset_name><@AssetId='...', ...>
  * GET /fwxapi/rest/v1/Dataset?pointname=...
- Target Equipments:
  * 10094 / MB003 (RMG)
  * 10110 / MB004 (FBD)
  * 10095 / MB005 (Blender)
  * 10040 / MB040 (Compression Machine)
  * 10141 / MB041 (Auto Coater)
- Continuous real-time streaming telemetry with realistic parameter fluctuations
  using persistent per-equipment state for smooth, continuous variation across polls.
- Each poll returns exactly ONE new telemetry point at the current timestamp.
"""

from __future__ import annotations

import json
import math
import os
import random
import sys
import time
import threading
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST = "0.0.0.0"
PORT = 8000

ASSET_EQUIPMENT_MAP = {
    "10094": "MB003",
    "10110": "MB004",
    "10095": "MB005",
    "10040": "MB040",
    "10141": "MB041",
}

EQUIPMENT_ASSET_MAP = {
    "MB003": "10094",
    "MB004": "10110",
    "MB005": "10095",
    "MB040": "10040",
    "MB041": "10141",
    "G5RMG": "10094",
    "G5FBD": "10110",
    "G5OGB": "10095",
    "G5COAT": "10141",
}

EQUIPMENT_FAMILY_MAP = {
    "MB003": "RMG",
    "MB004": "FBD",
    "MB005": "BLE",
    "MB040": "COMP",
    "MB041": "COAT",
    "G5RMG": "RMG",
    "G5FBD": "FBD",
    "G5OGB": "BLE",
    "G5COAT": "COAT",
}

# Try to load extracted reference data from reference_documents or data_service_layer
LOCAL_EXTRACTED_DATA_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "extracted_reference_data.json",
)
ROOT_EXTRACTED_DATA_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "reference_documents",
    "extracted_equipment_data.json",
)

REFERENCE_DOC_DATA: dict[str, dict] = {}
for path in (LOCAL_EXTRACTED_DATA_PATH, ROOT_EXTRACTED_DATA_PATH):
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                loaded = json.load(f)
                if loaded:
                    REFERENCE_DOC_DATA.update(loaded)
                    break
        except Exception as e:
            print(f"Warning: Failed to load {path}: {e}", file=sys.stderr)


# ==========================================
# Persistent Per-Equipment State Machine
# ==========================================
# Each equipment maintains a stateful context so that telemetry values
# change smoothly and continuously across polls (not reset each call).

_STATE_LOCK = threading.RLock()

# Per-equipment live state for smooth value generation
_EQUIPMENT_STATE: dict[str, dict] = {}

# Equipment state machine phases
EQUIPMENT_PHASES = {
    "RMG": ["DRY_CYCLE", "WET_MIXING", "WET_MIXING", "WET_MIXING", "IDLE"],
    "FBD": ["HEATING", "DRYING", "DRYING", "DRYING", "SHAKING", "DRYING", "IDLE"],
    "BLE": ["BATCH_READY", "MIXING_1", "MIXING_1", "MIXING_1", "MIXING_1_DONE", "MIXING_2", "MIXING_2", "BLENDING_OVER"],
    "COAT": ["PRE_HEATING", "SPRAYING", "SPRAYING", "SPRAYING", "SPRAYING", "SPRAYING", "POST_JOG"],
    "COMP": ["RUNNING", "RUNNING", "RUNNING", "RUNNING", "RUNNING", "IDLE"],
}

def _get_equipment_state(eq_code: str) -> dict:
    """Get or initialize persistent equipment state."""
    with _STATE_LOCK:
        if eq_code not in _EQUIPMENT_STATE:
            family = EQUIPMENT_FAMILY_MAP.get(eq_code, "RMG")
            _EQUIPMENT_STATE[eq_code] = _init_equipment_state(eq_code, family)
        return _EQUIPMENT_STATE[eq_code]


def _init_equipment_state(eq_code: str, family: str) -> dict:
    """Initialize equipment state with realistic starting values."""
    state = {
        "family": family,
        "phase_idx": 1,  # Start at first running phase (index 1)
        "phase_tick": 0,
        "phase_duration": random.randint(20, 60),  # ticks in current phase
        "poll_count": 0,
        "machine_state": "RUNNING",
        # Tablet count: monotonically increasing for COMP
        "tablet_count": random.randint(150000, 200000),
    }

    if family == "RMG":
        state.update({
            "impeller_amp": 22.0,
            "chopper_amp": 4.8,
            "pump_rpm": 60.0,
        })
    elif family == "FBD":
        state.update({
            "inlet_temp": 50.0,
            "exhaust_temp": 32.0,
            "shaking": False,
        })
    elif family == "BLE":
        state.update({
            "blender_speed": 6.0,
            "mixing_no": 1,
            "countdown_sec": 600,   # 10 minutes in seconds
            "vacuum": True,
        })
    elif family == "COAT":
        state.update({
            "inlet_temp": 45.0,
            "exhaust_temp": 40.0,
            "bed_temp": 38.0,
            "dosing_rpm": 14.0,
            "pan_speed": 2.1,
            "cycle_counter": 0,
            "coat_status": "SPRAYING",
        })
    elif family == "COMP":
        state.update({
            "turret_rpm": 32.5,
            "main_force": 15.2,
            "pre_force": 4.5,
        })
    return state


def _smooth_walk(current: float, target_min: float, target_max: float, max_delta: float) -> float:
    """Move current value toward the range center with bounded random walk."""
    center = (target_min + target_max) / 2.0
    # Gentle drift toward center + small random noise
    drift = (center - current) * 0.05
    noise = random.uniform(-max_delta, max_delta)
    new_val = current + drift + noise
    return max(target_min, min(target_max, new_val))


def _advance_equipment_state(state: dict) -> None:
    """Advance the equipment state by one poll tick (10 seconds)."""
    state["poll_count"] += 1
    state["phase_tick"] += 1

    family = state["family"]
    phases = EQUIPMENT_PHASES.get(family, ["RUNNING"])

    # Occasionally transition phase
    if state["phase_tick"] >= state["phase_duration"]:
        state["phase_tick"] = 0
        state["phase_duration"] = random.randint(15, 50)
        state["phase_idx"] = (state["phase_idx"] + 1) % len(phases)

    current_phase = phases[state["phase_idx"]]

    # Occasionally enter brief IDLE or ALARM state (~5% probability)
    alarm_roll = random.random()
    if alarm_roll < 0.02:
        state["machine_state"] = "ALARM"
    elif alarm_roll < 0.05:
        state["machine_state"] = "IDLE"
    else:
        state["machine_state"] = "RUNNING"

    if family == "RMG":
        if current_phase == "DRY_CYCLE":
            state["impeller_amp"] = _smooth_walk(state["impeller_amp"], 18.0, 20.0, 0.3)
            state["chopper_amp"] = _smooth_walk(state["chopper_amp"], 3.5, 4.0, 0.1)
            state["pump_rpm"] = 60.0
        else:  # WET_MIXING
            state["impeller_amp"] = _smooth_walk(state["impeller_amp"], 21.0, 26.7, 0.4)
            state["chopper_amp"] = _smooth_walk(state["chopper_amp"], 4.5, 6.2, 0.15)
            state["pump_rpm"] = 60.0 if state["machine_state"] == "RUNNING" else 0.0

    elif family == "FBD":
        if current_phase == "HEATING":
            state["inlet_temp"] = _smooth_walk(state["inlet_temp"], 40.0, 61.0, 1.0)
            state["exhaust_temp"] = _smooth_walk(state["exhaust_temp"], 26.0, 38.0, 0.8)
            state["shaking"] = False
        elif current_phase == "SHAKING":
            state["inlet_temp"] = _smooth_walk(state["inlet_temp"], 30.0, 50.0, 0.5)
            state["exhaust_temp"] = _smooth_walk(state["exhaust_temp"], 20.0, 35.0, 0.4)
            state["shaking"] = True
        else:  # DRYING
            state["inlet_temp"] = _smooth_walk(state["inlet_temp"], 50.0, 61.0, 0.6)
            state["exhaust_temp"] = _smooth_walk(state["exhaust_temp"], 35.0, 49.0, 0.5)
            state["shaking"] = False

    elif family == "BLE":
        # countdown decrements by 10 seconds per poll tick
        if state["countdown_sec"] > 0 and state["machine_state"] == "RUNNING":
            state["countdown_sec"] = max(0, state["countdown_sec"] - 10)

        if current_phase in ("BATCH_READY",):
            state["blender_speed"] = 0.0
            state["vacuum"] = False
            state["mixing_no"] = 1
        elif current_phase in ("MIXING_1", "MIXING_1_DONE"):
            state["blender_speed"] = _smooth_walk(state["blender_speed"], 5.9, 6.1, 0.02)
            state["vacuum"] = True
            state["mixing_no"] = 1
            if current_phase == "MIXING_1" and state["countdown_sec"] <= 0:
                # Reset countdown for mixing 2
                state["countdown_sec"] = 300  # 5 minutes
        elif current_phase == "MIXING_2":
            state["blender_speed"] = _smooth_walk(state["blender_speed"], 5.9, 6.1, 0.02)
            state["vacuum"] = True
            state["mixing_no"] = 2
        else:  # BLENDING_OVER
            state["blender_speed"] = 0.0
            state["vacuum"] = False

    elif family == "COAT":
        if current_phase == "PRE_HEATING":
            state["inlet_temp"] = _smooth_walk(state["inlet_temp"], 29.9, 55.0, 1.5)
            state["exhaust_temp"] = _smooth_walk(state["exhaust_temp"], 31.7, 44.0, 1.0)
            state["bed_temp"] = _smooth_walk(state["bed_temp"], 27.2, 42.0, 1.0)
            state["dosing_rpm"] = 0.0
            state["pan_speed"] = _smooth_walk(state["pan_speed"], 0.5, 2.1, 0.1)
            state["coat_status"] = "PRE-HEATING"
        elif current_phase == "SPRAYING":
            state["inlet_temp"] = _smooth_walk(state["inlet_temp"], 57.0, 63.4, 0.5)
            state["exhaust_temp"] = _smooth_walk(state["exhaust_temp"], 48.2, 49.3, 0.3)
            state["bed_temp"] = _smooth_walk(state["bed_temp"], 48.6, 50.5, 0.2)
            state["dosing_rpm"] = _smooth_walk(state["dosing_rpm"], 13.6, 15.6, 0.15)
            state["pan_speed"] = _smooth_walk(state["pan_speed"], 2.0, 2.2, 0.02)
            state["cycle_counter"] = min(265, state["cycle_counter"] + random.randint(1, 4))
            state["coat_status"] = "SPRAYING"
        else:  # POST_JOG
            state["inlet_temp"] = _smooth_walk(state["inlet_temp"], 29.0, 50.0, 1.0)
            state["exhaust_temp"] = _smooth_walk(state["exhaust_temp"], 27.4, 45.0, 0.8)
            state["bed_temp"] = _smooth_walk(state["bed_temp"], 23.6, 45.0, 0.8)
            state["dosing_rpm"] = 0.0
            state["pan_speed"] = _smooth_walk(state["pan_speed"], 1.0, 2.0, 0.05)
            state["coat_status"] = "POST-JOG"

    elif family == "COMP":
        state["turret_rpm"] = _smooth_walk(state["turret_rpm"], 25.0, 40.0, 0.8)
        state["main_force"] = _smooth_walk(state["main_force"], 14.0, 17.5, 0.3)
        state["pre_force"] = _smooth_walk(state["pre_force"], 4.0, 5.5, 0.12)
        # Tablet count monotonically increases while RUNNING
        if state["machine_state"] == "RUNNING":
            # ~100 tablets per 10s at 32 RPM (32 * 10 = 320 tablets/s * time = conservative estimate)
            tablets_per_poll = int(state["turret_rpm"] * 10 * random.uniform(0.8, 1.2))
            state["tablet_count"] += tablets_per_poll


def resolve_equipment_code(dataset_id: str, asset_id: str | None = None) -> str:
    if asset_id and str(asset_id).strip() in ASSET_EQUIPMENT_MAP:
        return ASSET_EQUIPMENT_MAP[str(asset_id).strip()]

    clean = (dataset_id or "").strip().upper()
    if clean in ("MB003", "MB004", "MB005", "MB040", "MB041"):
        return clean
    if clean in ("G5RMG", "RMGC0219", "RMG"):
        return "MB003"
    if clean in ("G5FBD", "FBDC0220", "FBD"):
        return "MB004"
    if clean in ("G5OGB", "OCBC0222", "BLE", "OGB", "OCB"):
        return "MB005"
    if clean in ("MB040", "COMP"):
        return "MB040"
    if clean in ("G5COAT", "COATC0223", "COATC0226", "COAT"):
        return "MB041"

    # Digits fallback
    for aid, eq in ASSET_EQUIPMENT_MAP.items():
        if aid in clean:
            return eq
    return "MB003"


def dataset_family(dataset_id: str, asset_id: str | None = None) -> str:
    eq_code = resolve_equipment_code(dataset_id, asset_id)
    return EQUIPMENT_FAMILY_MAP.get(eq_code, "RMG")


def parse_pointname(pointname: str):
    """Return dataset_id, dataset_name, params from pointname.

    Handles:
      db:HMI.Batch_Info<@AssetId='10094', @Batch_No=''>
      db:HMI.RMG_op_data<@AssetId='10094', @BatchNo='AGO0026016', @LotNo='01'>
      db:G5RMG.BATCHDATA<@BATCH_NO='AGO0026016', @LOT_NO='01'>
      db:MB003.BATCHDETAILS
    """
    name = (pointname or "").strip()
    params = {}
    dataset_id = "HMI"

    if "<" in name and ">" in name:
        left = name.index("<") + 1
        right = name.index(">")
        inner = name[left:right]
        name = name[: name.index("<")].strip()
        for part in inner.split(","):
            if "=" not in part:
                continue
            key, value = [item.strip() for item in part.split("=", 1)]
            params[key.lstrip("@")] = value.strip("'\"")

    if "." in name:
        parts = name.split(".")
        dataset_id = parts[0].replace("db:", "").strip()
        dataset_name = parts[-1].strip()
    else:
        dataset_name = name.replace("db:", "").strip()

    # Determine assetId or equipmentCode
    asset_id = params.get("AssetId") or params.get("AssetID") or params.get("assetId") or params.get("asset_id")
    if not asset_id:
        if dataset_id in EQUIPMENT_ASSET_MAP:
            asset_id = EQUIPMENT_ASSET_MAP[dataset_id]
        elif dataset_id.upper() in EQUIPMENT_ASSET_MAP:
            asset_id = EQUIPMENT_ASSET_MAP[dataset_id.upper()]

    if asset_id:
        params["AssetId"] = str(asset_id)

    return dataset_id, dataset_name, params


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
            return f"{year}-{month.zfill(2)}-{day.zfill(2)}T{time_part}"
    return s


def build_batch_info(eq_code: str):
    """Return Batch_Info / BATCHDETAILS payload for equipment."""
    ref = REFERENCE_DOC_DATA.get(eq_code, {})
    b = ref.get("batchDetails", {})

    # Defaults per equipment derived from authentic PDF batch reports & master recipes
    _defaults = {
        "MB003": ("AGO0026016", "01",  "LAMOTRIGINE",          "STGW2000", "Lamotrigine Granulation & Drying Recipe (AGO)",  "248.640"),
        "MB004": ("AGO0026016", "1B",  "LAMOTRIGINE",          "STGW2000", "Lamotrigine Granulation & Drying Recipe (AGO)",  "248.640"),
        "MB005": ("AGO0026015", "01",  "LAMOTRIGINE",          "STGW2000", "Lamotrigine Octagonal Blending Recipe (AGO0026015)",  "248.640"),
        "MB041": ("PED26009",   "NA",  "PAROXETINE USP 40 mg", "STPA1D00", "Paroxetine USP 40mg Film Coating Recipe (PAROXE40)",    "625000"),
        "MB040": ("Pb1 Mb Compression", "01", "LAMOTRIGINE",   "STGW2000", "Lamotrigine Compression Recipe (COMP)",        "248.640"),
    }
    defs = _defaults.get(eq_code, _defaults["MB003"])
    batch_no   = b.get("batchNumber", defs[0])
    lot_no     = b.get("lotNumber",   defs[1])
    prod_name  = b.get("productName", defs[2])
    prod_code  = b.get("productCode", defs[3])
    recipe_name= b.get("recipeName",  defs[4])
    batch_size_raw = str(b.get("batchSize", defs[5])).split()[0]
    try:
        batch_size = float(batch_size_raw)
    except ValueError:
        batch_size = 0.0

    batch_size_unit = b.get("batchSizeUnit", "Kgs")

    now = datetime.now()
    start_dt = now - timedelta(hours=3)

    return [
        {
            "BatchNo":         batch_no,
            "LotNo":           lot_no,
            "ProductNo":       prod_code,
            "ProductName":     prod_name,
            "PRODUCT_NAME":    prod_name,
            "PRODUCT_CODE":    prod_code,
            "RECIPE_NAME":     recipe_name,
            "BATCH_NO":        batch_no,
            "LOT_NO":          lot_no,
            "BATCH_SIZE_KG":   batch_size,
            "BATCH_SIZE_UNIT": batch_size_unit,
            "BatchStartDate":  start_dt.strftime("%Y-%m-%dT%H:%M:%S"),
            "BatchEndDate":    None,
        }
    ]


def build_batch_summary(eq_code: str, batch_no: str | None = None, lot_no: str | None = None):
    ref = REFERENCE_DOC_DATA.get(eq_code, {})
    b = ref.get("batchDetails", {})
    defs = {
        "MB003": ("AGO0026016", "01", "LAMOTRIGINE", "Lamotrigine Granulation & Drying Recipe (AGO)", "248.640 Kg"),
        "MB004": ("AGO0026016", "1B", "LAMOTRIGINE", "Lamotrigine Granulation & Drying Recipe (AGO)", "248.640 Kg"),
        "MB005": ("AGO0026015", "01", "LAMOTRIGINE", "Lamotrigine Octagonal Blending Recipe (AGO0026015)", "248.640 Kg"),
        "MB041": ("PED26009",   "NA", "PAROXETINE USP 40 mg", "Paroxetine USP 40mg Film Coating Recipe (PAROXE40)", "625000 Tablets"),
        "MB040": ("Pb1 Mb Compression", "01", "LAMOTRIGINE", "Lamotrigine Compression Recipe (COMP)", "248.640 Kg"),
    }.get(eq_code, ("AGO0026016", "01", "LAMOTRIGINE", "Lamotrigine Granulation & Drying Recipe (AGO)", "248.640 Kg"))

    b_no = batch_no or b.get("batchNumber", defs[0])
    l_no = lot_no or b.get("lotNumber", defs[1])
    prod_name = b.get("productName", defs[2])
    recipe_name = b.get("recipeName", defs[3])
    batch_size = b.get("batchSize", defs[4])

    return [
        {
            "Batch Number": b_no,
            "Lot Number": l_no,
            "Product Name": prod_name,
            "Recipe Name": recipe_name,
            "Batch Size": batch_size,
        }
    ]


def build_mach_summary(eq_code: str):
    ref = REFERENCE_DOC_DATA.get(eq_code, {})
    eq = ref.get("equipmentDetails", {})
    return [
        {
            "Equipment Name": eq.get("equipmentName", "EQUIPMENT"),
            "Equipment ID": eq_code,
            "Make": eq.get("make", "MITSUBISHI"),
            "Block": eq.get("block", "PB1"),
            "Area": eq.get("area", "MODULE-B"),
        }
    ]


def build_user_login(eq_code: str):
    ref = REFERENCE_DOC_DATA.get(eq_code, {})
    users = ref.get("userLoginLogout", [])
    if users:
        return users
    # Consistent user mappings matching personnel spec
    _default_users = {
        "MB003": [
            {"User Name": "96828 (PB1-RMG (MB003) Operator)",      "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
            {"User Name": "96365 (PB1-RMG (MB003) Supervisor)",    "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
        ],
        "MB004": [
            {"User Name": "11173 (PB1-Module-B (MB004) Operator)", "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
            {"User Name": "191555 (PB1-Module-B (MB004) Supervisor)","Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
        ],
        "MB005": [
            {"User Name": "11173 (PB1-Module-B-Blender-Operator)",  "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
            {"User Name": "Harish Chandra Mishra-191164(PB1-Module-B-Blender-Supervisor)", "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
        ],
        "MB041": [
            {"User Name": "29995 (PB1-Module-B-Operator)",          "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
            {"User Name": "191257 (PB1-Module-B-Supervisor)",       "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
        ],
        "MB040": [
            {"User Name": "10401 (PB1-Compression-Operator)",       "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
            {"User Name": "10402 (PB1-Compression-Supervisor)",     "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"},
        ],
    }
    return _default_users.get(eq_code, [{"User Name": f"Operator ({eq_code})", "Date And Time": datetime.now().strftime("%d/%m/%Y %H:%M:%S"), "Description": "Login"}])


def build_streaming_telemetry(eq_code: str, batch_no: str | None = None, lot_no: str | None = None, points_count: int = 1):
    """Generate exactly ONE new real-time telemetry point at the current timestamp.

    Uses persistent per-equipment state for smooth, continuous variation.
    The scheduler calls this every 10 seconds; each call produces 1 new unique record.
    """
    family = EQUIPMENT_FAMILY_MAP.get(eq_code, "RMG")
    ref = REFERENCE_DOC_DATA.get(eq_code, {})
    b = ref.get("batchDetails", {})

    _defaults = {
        "MB003": ("AGO0026016", "01"),
        "MB004": ("AGO0026016", "1B"),
        "MB005": ("AGO0026015", "01"),
        "MB041": ("PED26009",   "NA"),
        "MB040": ("Pb1 Mb Compression", "01"),
    }
    defs = _defaults.get(eq_code, _defaults["MB003"])
    b_no = batch_no or b.get("batchNumber", defs[0])
    l_no = lot_no or b.get("lotNumber", defs[1])

    # Operator mapping (consistent with personnel spec)
    operator_map = {
        "MB003": "96828 (PB1-RMG (MB003) Operator)",
        "MB004": "11173 (PB1-Module-B (MB004) Operator)",
        "MB005": "11173 (PB1-Module-B-Blender-Operator)",
        "MB040": "10401 (PB1-Compression-Operator)",
        "MB041": "29995 (PB1-Module-B-Operator)",
    }
    operator = operator_map.get(eq_code, "PB1 Operator")

    # Advance and read persistent state
    with _STATE_LOCK:
        state = _get_equipment_state(eq_code)
        _advance_equipment_state(state)
        machine_state = state["machine_state"]
        current_phase_phases = EQUIPMENT_PHASES.get(family, ["RUNNING"])
        current_phase = current_phase_phases[state["phase_idx"]]

    now = datetime.now()
    time_iso = now.strftime("%Y-%m-%dT%H:%M:%S")
    time_display = now.strftime("%d/%m/%Y %H:%M:%S")

    record: dict = {
        "DT": time_iso,
        "TIME": time_display,
        "TimeStamp": time_iso,
        "timestamp": time_iso,
        "Batch_No": b_no,
        "batch_no": b_no,
        "BATCH_NO": b_no,
        "Lot_No": l_no,
        "lot_no": l_no,
        "LOT_NO": l_no,
        "Status": machine_state,
        "status": machine_state,
        "User_Name": operator,
        "user_name": operator,
        "EquipmentCode": eq_code,
        "equipment_code": eq_code,
        "EquipmentType": family,
        "equipment_type": family,
    }

    if family == "RMG":
        record.update({
            "CURRENT (Amp)":        round(state["impeller_amp"], 2),
            "Impeller_Current_Amp": round(state["impeller_amp"], 2),
            "Chopper_Current_Amp":  round(state["chopper_amp"], 2),
            "Pump_RPM":             state["pump_rpm"],
            "STATUS":               current_phase.replace("_", " "),
            "agAmps":               round(state["impeller_amp"], 2),
            "chpAmps":              round(state["chopper_amp"], 2),
            "pumpRpm":              state["pump_rpm"],
        })
    elif family == "FBD":
        shaking_str = "SHAKING" if state["shaking"] else "DRYING"
        record.update({
            "INLET TEMPARATURE":  round(state["inlet_temp"], 1),
            "EXHAUST TEMPARATURE": round(state["exhaust_temp"], 1),
            "Inlet_Temp":          round(state["inlet_temp"], 1),
            "Exhaust_Temp":        round(state["exhaust_temp"], 1),
            "Shaking_State":       shaking_str,
            "inletTemp":           round(state["inlet_temp"], 1),
            "exhaustTemp":         round(state["exhaust_temp"], 1),
            "STATUS":              shaking_str,
        })
    elif family == "BLE":
        countdown_min = round(state["countdown_sec"] / 60.0, 1)
        vacuum_str = "ON" if state["vacuum"] else "OFF"
        blender_status_map = {
            "BATCH_READY": "BATCH READY",
            "MIXING_1": "MIXING 1 STARTED",
            "MIXING_1_DONE": "MIXING 1 COMPLETED",
            "MIXING_2": "MIXING 2 STARTED",
            "BLENDING_OVER": "BLENDING OVER",
        }
        blender_status = blender_status_map.get(current_phase, "MIXING 1 STARTED")
        record.update({
            "BLENDING SPEED (RPM)":  round(state["blender_speed"], 2),
            "Blender_Speed_RPM":     round(state["blender_speed"], 2),
            "ACTUAL RPM":            6,
            "actualRpm":             6,
            "BLENDER STATUS":        blender_status,
            "blenderStatus":         blender_status,
            "Mixing_Countdown_Min":  countdown_min,
            "mixingCountdown":       countdown_min,
            "Vacuum_Status":         vacuum_str,
            "vacuumStatus":          vacuum_str,
            "Mixing_No":             state["mixing_no"],
            "mixingNo":              state["mixing_no"],
            "blenderSpeed":          round(state["blender_speed"], 2),
            "Select_No_Mixings":     2,
            "First_Mixing_Time_Min":    10,
            "Second_Mixing_Time_Min":   5,
            "STATUS":                blender_status,
        })
    elif family == "COAT":
        record.update({
            "INLET AIR TEMP (°C)":     round(state["inlet_temp"], 1),
            "EXHAUST AIR TEMP (°C)":   round(state["exhaust_temp"], 1),
            "BED TEMP (°C)":           round(state["bed_temp"], 1),
            "DOSING PUMP SPEED (RPM)": round(state["dosing_rpm"], 1),
            "PAN SPEED (RPM)":         round(state["pan_speed"], 2),
            "CYCLE COUNTER":           state["cycle_counter"],
            "Inlet_Air_Temp":          round(state["inlet_temp"], 1),
            "Exhaust_Air_Temp":        round(state["exhaust_temp"], 1),
            "Bed_Temp":                round(state["bed_temp"], 1),
            "Dosing_Speed_RPM":        round(state["dosing_rpm"], 1),
            "Pan_Speed_RPM":           round(state["pan_speed"], 2),
            "Cycle_Counter":           state["cycle_counter"],
            "inletAirTemp":            round(state["inlet_temp"], 1),
            "exhaustAirTemp":          round(state["exhaust_temp"], 1),
            "bedTemp":                 round(state["bed_temp"], 1),
            "dosingSpeed":             round(state["dosing_rpm"], 1),
            "panSpeed":                round(state["pan_speed"], 2),
            "cycleCounter":            state["cycle_counter"],
            "Coat_Status":             state["coat_status"],
            "coatStatus":              state["coat_status"],
            "STATUS":                  state["coat_status"],
        })
    elif family == "COMP":
        record.update({
            "Turret_RPM":            round(state["turret_rpm"], 1),
            "Main_Force_kN":         round(state["main_force"], 2),
            "Pre_Force_kN":          round(state["pre_force"], 2),
            "Tablet_Count":          state["tablet_count"],
            "turretRpm":             round(state["turret_rpm"], 1),
            "mainCompressionForce":  round(state["main_force"], 2),
            "preForce":              round(state["pre_force"], 2),
            "tabletCount":           state["tablet_count"],
            "STATUS":                machine_state,
        })

    # Return list with single record (ingestion processes list of records)
    return [record]


def build_parameter_settings(eq_code: str):
    """Return recipe / parameter settings for the given equipment."""
    ref = REFERENCE_DOC_DATA.get(eq_code, {})
    settings = ref.get("parameterSettings", {})
    # Wrap flat BLE params into a labelled group for API consistency
    if settings and isinstance(settings, dict) and not any(isinstance(v, dict) for v in settings.values()):
        return {"RECIPE PARAMETERS": settings}
    return settings


def build_operational_values(eq_code: str):
    """Return min/max operational value summary (COAT equipment)."""
    ref = REFERENCE_DOC_DATA.get(eq_code, {})
    return ref.get("operationalValues", {})


def _normalise_alarm(alarm: dict, eq_code: str, family: str) -> dict:
    """Normalise alarm record keys for ingestion pipeline compatibility."""
    now_str = datetime.now().strftime("%d/%m/%Y %H:%M:%S")
    return {
        "MsgNumber":      alarm.get("MsgNumber") or alarm.get("msgNumber", 0),
        "DT":             _to_iso(alarm.get("Occured Time") or alarm.get("occurredTime") or alarm.get("dt") or now_str),
        "Alarm_Name":     alarm.get("Alarm Name") or alarm.get("alarmName", f"{family}: ALARM"),
        "Occurred_Time":  alarm.get("Occured Time") or alarm.get("occurredTime") or now_str,
        "Resolved_Time":  alarm.get("Resolved Time") or alarm.get("resolvedTime") or now_str,
        "Duration":       alarm.get("Duration (HH:MM:SS)") or alarm.get("duration", "00:00:00"),
        "MsgText":        alarm.get("MsgText") or alarm.get("msgText") or alarm.get("Alarm Name") or alarm.get("alarmName", ""),
        "EquipmentCode":  eq_code,
        "EquipmentType":  family,
        "StateAfter":     alarm.get("StateAfter", 1),
    }


def build_alarm_data(eq_code: str, from_time: str, to_time: str):
    family = EQUIPMENT_FAMILY_MAP.get(eq_code, "RMG")
    ref = REFERENCE_DOC_DATA.get(eq_code, {})
    alarms = ref.get("alarmSummary", [])
    if alarms:
        return [_normalise_alarm(a, eq_code, family) for a in alarms]

    # No alarms in reference → return a benign status record
    now_str = datetime.now().strftime("%d/%m/%Y %H:%M:%S")
    return [
        {
            "MsgNumber":     101,
            "DT":            _to_iso(now_str),
            "Alarm_Name":    f"{family}: SYSTEM OK / NO ACTIVE FAULTS",
            "Occurred_Time": now_str,
            "Resolved_Time": now_str,
            "Duration":      "00:00:05",
            "MsgText":       f"{family}: Status Normal",
            "EquipmentCode": eq_code,
            "EquipmentType": family,
            "StateAfter":    1,
        }
    ]


def _normalise_audit(audit: dict, seq: int, eq_code: str, family: str) -> dict:
    """Normalise audit trail record keys for ingestion pipeline compatibility."""
    dt_raw = audit.get("Date Time") or audit.get("dateTime") or audit.get("dt") or ""
    return {
        "RecordID":      audit.get("recordId") or f"AUD-{eq_code}-{seq:02d}",
        "DT":            _to_iso(dt_raw),
        "DateTime":      dt_raw,
        "TimeStamp":     dt_raw,
        "Description":   audit.get("Description") or audit.get("description", "-"),
        "OldValue":      audit.get("Old Value") or audit.get("oldValue", "-"),
        "NewValue":      audit.get("New Value") or audit.get("newValue", "-"),
        "Reason":        audit.get("Reason") or audit.get("reason", "-"),
        "UserName":      audit.get("User Name") or audit.get("userName") or f"Operator ({eq_code})",
        "EquipmentCode": eq_code,
        "EquipmentType": family,
    }


def build_audit_data(eq_code: str, from_time: str, to_time: str):
    ref = REFERENCE_DOC_DATA.get(eq_code, {})
    audits = ref.get("auditTrail", [])
    family = EQUIPMENT_FAMILY_MAP.get(eq_code, "RMG")
    if audits:
        return [_normalise_audit(a, i + 1, eq_code, family) for i, a in enumerate(audits)]

    now_str = datetime.now().strftime("%d/%m/%Y %H:%M:%S")
    return [
        {
            "RecordID":      f"AUD-{eq_code}-01",
            "DT":            _to_iso(now_str),
            "DateTime":      now_str,
            "TimeStamp":     now_str,
            "Description":   "BATCH RUNNING",
            "OldValue":      "-",
            "NewValue":      "-",
            "Reason":        "-",
            "UserName":      f"Operator ({eq_code})",
            "EquipmentCode": eq_code,
            "EquipmentType": family,
        }
    ]


class MockDataServiceHandler(BaseHTTPRequestHandler):
    """Handles REST GET queries for dataset point names."""

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        valid_prefixes = (
            "/fwxapi/rest/v1/Dataset",
            "/Dataset",
            "/api/v1/Dataset",
        )
        if not any(parsed.path.startswith(prefix) for prefix in valid_prefixes):
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'{"error": "Not Found"}')
            return

        query_params = parse_qs(parsed.query)
        pointname = (
            query_params.get("pointname", [""])[0]
            or query_params.get("pointName", [""])[0]
            or query_params.get("point_name", [""])[0]
        )
        if not pointname:
            # Fallback check from path segments
            path_parts = parsed.path.rstrip("/").split("/")
            if path_parts and path_parts[-1] not in ("Dataset", "v1"):
                pointname = f"db:HMI.{path_parts[-1]}"

        dataset_id, dataset_name, params = parse_pointname(pointname)
        qp_asset = (query_params.get("AssetId", [""])[0] or query_params.get("assetId", [""])[0] or query_params.get("AssetID", [""])[0] or query_params.get("asset_id", [""])[0]).strip()
        asset_id = qp_asset or params.get("AssetId") or params.get("AssetID") or None
        eq_code = resolve_equipment_code(dataset_id, asset_id)

        clean_dataset = dataset_name.upper().replace("_", "")
        payload: object = []

        qp_batch = (query_params.get("BatchNo", [""])[0] or query_params.get("batchNo", [""])[0] or query_params.get("batch_no", [""])[0] or query_params.get("BATCH_NO", [""])[0]).strip()
        qp_lot = (query_params.get("LotNo", [""])[0] or query_params.get("lotNo", [""])[0] or query_params.get("lot_no", [""])[0] or query_params.get("LOT_NO", [""])[0]).strip()
        batch_no = qp_batch or params.get("BatchNo") or params.get("BATCH_NO") or params.get("batch_no") or None
        lot_no = qp_lot or params.get("LotNo") or params.get("LOT_NO") or params.get("lot_no") or None

        if clean_dataset in ("BATCHDETAILS", "BATCHINFO"):
            payload = build_batch_info(eq_code)
        elif clean_dataset in ("BATCHSUMMARY",):
            payload = build_batch_summary(eq_code, batch_no, lot_no)
        elif clean_dataset in ("MACHSUMMARY",):
            payload = build_mach_summary(eq_code)
        elif clean_dataset in ("LOGIN", "USERS"):
            payload = build_user_login(eq_code)
        elif clean_dataset in ("PARAMETERSETTINGS", "RECIPE", "MINMAX") or "RECIPE" in clean_dataset or "MINMAX" in clean_dataset:
            payload = build_parameter_settings(eq_code)
        elif clean_dataset in ("OPERATIONALVALUES", "OPVALUES"):
            payload = build_operational_values(eq_code)
        elif clean_dataset in ("ALARMDATA", "ALARMS", "ALARMSUMMARY"):
            payload = build_alarm_data(eq_code, params.get("FROMTIME", ""), params.get("TOTIME", ""))
        elif clean_dataset in ("AUDITDATA", "AUDITTRAIL", "AUDIT"):
            payload = build_audit_data(eq_code, params.get("FROMTIME", ""), params.get("TOTIME", ""))
        else:
            # BATCHDATA, RMG_op_data, FBD_Op_Data, BLE_Op_Data, COAT_Op_Data, COMP_Op_Data, etc.
            # Returns exactly 1 new record with advancing timestamp
            payload = build_streaming_telemetry(eq_code, batch_no, lot_no, points_count=1)

        response_payload = {
            "status": "success",
            "pointname": pointname,
            "dataset_id": dataset_id,
            "dataset": dataset_name,
            "equipmentCode": eq_code,
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
    print(f"Mock Data Service running on http://{host}:{port}", flush=True)
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
