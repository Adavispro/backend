#!/usr/bin/env python3
"""Mock data service that mimics the source API used by the scheduler.

Matches endpoints defined in:
- data_service_layer/script_requirement.md
- scheduler/requirement.md
- reference_documents/reports (RMG.pdf, FBD.pdf, BLE.pdf, COAT.pdf)

Exposes sample data for:
- BATCHDETAILS
- BATCHDATA
- ALARMDATA
- AUDITDATA
- PARAMETERSETTINGS
"""

from __future__ import annotations

import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST = "0.0.0.0"
PORT = 8000

# Try to load extracted reference data from reference_documents/extracted_equipment_data.json
EXTRACTED_DATA_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "reference_documents",
    "extracted_equipment_data.json",
)

REFERENCE_DOC_DATA: dict[str, dict] = {}
if os.path.exists(EXTRACTED_DATA_PATH):
    try:
        with open(EXTRACTED_DATA_PATH, "r", encoding="utf-8") as f:
            REFERENCE_DOC_DATA = json.load(f)
    except Exception as e:
        print(f"Warning: Failed to load {EXTRACTED_DATA_PATH}: {e}", file=sys.stderr)


def dataset_family(dataset_id: str) -> str:
    cleaned = (
        (dataset_id or "G5RMG")
        .upper()
        .replace("PB3", "")
        .replace("C0219", "RMG")
        .replace("C0220", "FBD")
        .replace("C0222", "BLE")
        .replace("C0223", "COAT")
        .replace("C0224", "CIP")
    )
    if "RMG" in cleaned:
        return "RMG"
    if "FBD" in cleaned:
        return "FBD"
    if "BLE" in cleaned or "OCB" in cleaned or "OGB" in cleaned:
        return "BLE"
    if "COAT" in cleaned:
        return "COAT"
    if "CIP" in cleaned:
        return "CIP"
    return (dataset_id or "G5RMG")[-3:].upper()


def dataset_number(dataset_id: str) -> int:
    text = str(dataset_id or "")
    digits = "".join(ch for ch in text if ch.isdigit())
    return int(digits[-1]) if digits else 5


def parse_pointname(pointname: str):
    """Return the dataset id, dataset name, and parameter map from a source pointname string.

    Examples:
        db:G5RMG.BATCHDATA<@BATCH_NO='NL0026008', @LOT_NO='01 of 05'>
        db:G5RMG.PARAMETERSETTINGS<@BATCH_NO='NL0026008', @LOT_NO='01 of 05'>
        db:G5RMG.ALARMDATA<@FROMTIME='2026-08-13 06:57:45', @TOTIME='2026-08-13 13:39:54'>
    """
    name = pointname.strip()
    params = {}

    dataset_id = "G5RMG"

    if "<" in name and ">" in name:
        left = name.index("<") + 1
        right = name.index(">")
        inner = name[left:right]
        name = name[: name.index("<")]
        for part in inner.split(","):
            if "=" not in part:
                continue
            key, value = [item.strip() for item in part.split("=", 1)]
            params[key.lstrip("@")] = value.strip("'\"")

    if "." in name:
        dataset_id = name.split(".")[0].replace("db:", "")
        dataset_name = name.split(".")[-1]
    else:
        dataset_name = name

    return dataset_id, dataset_name, params


def build_batch_details(dataset_id: str):
    family = dataset_family(dataset_id)
    # CIP excluded from active data load
    if family == "CIP":
        return []

    # RMG, FBD, BLE, COAT: 1 batch and 1 lot matching reference document
    return [
        {
            "PRODUCT_NAME": "Finasteride USP 5 mg",
            "PRODUCT_CODE": "STFS7000",
            "RECIPE_NAME": "STFS7000",
            "BATCH_NO": "NL0026008",
            "LOT_NO": "01 of 05",
            "BATCH_SIZE_KG": 900.0,
        }
    ]


def build_parameter_settings(dataset_id: str, batch_no: str, lot_no: str):
    family = dataset_family(dataset_id)
    ref = REFERENCE_DOC_DATA.get(family, {})
    if ref and "parameterSettings" in ref:
        return ref["parameterSettings"]

    if family == "RMG":
        return {
            "dryCycle1": {
                "DRY CYCLE1 IMPELLER SLOW SET (Sec)": 600,
                "DRY CYCLE1 IMPELLER FAST SET (Sec)": 0,
                "DRY CYCLE1 CHOPPER DELAY (Sec)": 0,
                "DRY CYCLE1 CHOPPER SLOW SET (Sec)": 0,
                "DRY CYCLE1 CHOPPER FAST SET (Sec)": 0,
            },
            "wetCycle1": {
                "WET CYCLE1 IMPELLER SLOW SET (Sec)": 180,
                "WET CYCLE1 IMPELLER FAST SET (Sec)": 0,
                "WET CYCLE1 CHOPPER DELAY (Sec)": 0,
                "WET CYCLE1 CHOPPER SLOW SET (Sec)": 0,
                "WET CYCLE1 CHOPPER FAST SET (Sec)": 0,
                "WET CYCLE1 PUMP1 ON DELAY (Sec)": 0,
                "WET CYCLE1 PUMP1 SET (Sec)": 180,
                "WET CYCLE1 PUMP1 RPM": 240,
            },
            "wetCycle2": {
                "WET CYCLE2 IMPELLER SLOW SET (Sec)": 180,
                "WET CYCLE2 IMPELLER FAST SET (Sec)": 0,
                "WET CYCLE2 CHOPPER DELAY (Sec)": 0,
                "WET CYCLE2 CHOPPER SLOW SET (Sec)": 180,
                "WET CYCLE2 CHOPPER FAST SET (Sec)": 0,
                "WET CYCLE2 PUMP1 ON DELAY (Sec)": 0,
                "WET CYCLE2 PUMP1 SET (Sec)": 0,
                "WET CYCLE2 PUMP1 RPM": 0,
            },
            "wetCycle3": {
                "WET CYCLE3 IMPELLER SLOW SET (Sec)": 480,
                "WET CYCLE3 IMPELLER FAST SET (Sec)": 0,
                "WET CYCLE3 CHOPPER DELAY (Sec)": 0,
                "WET CYCLE3 CHOPPER SLOW SET (Sec)": 480,
                "WET CYCLE3 CHOPPER FAST SET (Sec)": 0,
                "WET CYCLE3 PUMP1 ON DELAY (Sec)": 0,
                "WET CYCLE3 PUMP1 SET (Sec)": 0,
                "WET CYCLE3 PUMP1 RPM": 0,
            },
            "unloadingParameters": {
                "IMPELLER": "SLOW",
                "CHOPPER": "SLOW",
            },
        }

    if family == "BLE":
        return {
            "SELECT NUMBER OF MIXINGS": 2,
            "FIRST MIXING TIME (MIN)": 15,
            "SECOND MIXING TIME (MIN)": 5,
            "THIRD MIXING TIME (MIN)": 0,
            "FOURTH MIXING TIME (MIN)": 0,
            "BLENDING SPEED (RPM)": 5,
            "VACUUM ON TIME (MIN)": 100,
            "PURGE ON TIME (Sec)": 5,
        }

    if family == "COAT":
        return {
            "PRE_HEATING": {
                "INLET AIR TEMP SET (C)": 65,
                "BED TEMP SET (C)": 42,
                "PAN SPEED SET (RPM)": 3,
                "DRYING TIME (MIN)": 15,
            },
            "SPRAYING_CYCLE": {
                "INLET AIR TEMP SET (C)": 65,
                "BED TEMP SET (C)": 44,
                "PAN SPEED SET (RPM)": 8,
                "SPRAY RATE SET (G/MIN)": 120,
                "ATOMIZING AIR PRESSURE (BAR)": 2.5,
                "PATTERN AIR PRESSURE (BAR)": 2.0,
                "PROCESS TIME (MIN)": 180,
            },
            "POST_DRYING": {
                "INLET AIR TEMP SET (C)": 50,
                "BED TEMP SET (C)": 40,
                "PAN SPEED SET (RPM)": 3,
                "DRYING TIME (MIN)": 30,
            },
        }

    # FBD
    return {
        "PROCESS TIME (MIN)": 300,
        "AIR DRY TIME (MIN)": 5,
        "COOLING TIME (MIN)": 0,
        "SHAKE INTERVAL (MIN)": 10,
        "SHAKE DURATION (SEC)": 30,
        "END SHAKE TIME (SEC)": 30,
        "INLET TEMPERATURE (C)": 60,
        "INLET TEMPERATURE HIGH (C)": 64,
        "OUTLET TEMPERATURE (C)": 48,
        "PRINT INTERVAL (MIN)": 5,
    }


def _to_iso(dt_str: str) -> str:
    """Convert 'DD/MM/YYYY HH:MM:SS' to 'YYYY-MM-DDTHH:MM:SS'."""
    s = str(dt_str or "").strip()
    if not s:
        return "2026-02-09T18:00:00"
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


def build_batch_data(dataset_id: str, batch_no: str, lot_no: str):
    family = dataset_family(dataset_id)
    if family == "CIP":
        return []

    user_name = {
        "RMG": "91525 (PB3 RMGC0219 Operator)",
        "FBD": "91525 (PB3 FBDC0220 Operator)",
        "BLE": "91525 (PB3 OCBC0222 Operator)",
        "COAT": "91525 (PB3 COATC0223 Operator)",
    }.get(family, "91525 (PB3 Operator)")

    # 1. RMG Telemetry
    if family == "RMG":
        rmg_raw = [
            ("09/02/2026 18:02:40", "DRY CYCLE 1 IMPELLER SLOW START", 24.5, 0, 75.0, 0.0, 28.5),
            ("09/02/2026 18:12:40", "DRY CYCLE 1 IMPELLER SLOW STOP", 25.1, 600, 75.0, 0.0, 29.0),
            ("09/02/2026 18:16:03", "WET CYCLE 1 IMPELLER SLOW START", 26.2, 0, 75.0, 0.0, 29.5),
            ("09/02/2026 18:16:03", "WET CYCLE 1 PUMP 1 START", 26.2, 0, 75.0, 0.0, 29.5),
            ("09/02/2026 18:18:38", "WET CYCLE 1 IMPELLER SLOW STOP", 30.5, 155, 75.0, 0.0, 30.0),
            ("09/02/2026 18:18:38", "WET CYCLE 1 PUMP 1 STOP", 30.5, 155, 75.0, 0.0, 30.0),
            ("09/02/2026 18:19:50", "WET CYCLE 1 IMPELLER SLOW START", 27.0, 0, 75.0, 0.0, 30.1),
            ("09/02/2026 18:19:50", "WET CYCLE 1 PUMP 1 START", 27.0, 0, 75.0, 0.0, 30.1),
            ("09/02/2026 18:20:15", "WET CYCLE 1 IMPELLER SLOW STOP", 30.4, 25, 75.0, 0.0, 30.2),
            ("09/02/2026 18:20:15", "WET CYCLE 1 PUMP 1 STOP", 30.4, 25, 75.0, 0.0, 30.2),
            ("09/02/2026 18:22:26", "WET CYCLE 2 IMPELLER SLOW START", 28.0, 0, 75.0, 1500.0, 30.5),
            ("09/02/2026 18:22:26", "WET CYCLE 2 CHOPPER SLOW START", 28.0, 0, 75.0, 1500.0, 30.5),
            ("09/02/2026 18:23:24", "WET CYCLE 2 IMPELLER SLOW STOP", 30.6, 58, 75.0, 1500.0, 30.8),
            ("09/02/2026 18:23:24", "WET CYCLE 2 CHOPPER SLOW STOP", 30.6, 58, 75.0, 1500.0, 30.8),
            ("09/02/2026 18:26:03", "WET CYCLE 2 IMPELLER SLOW START", 28.5, 0, 75.0, 1500.0, 31.0),
            ("09/02/2026 18:26:03", "WET CYCLE 2 CHOPPER SLOW START", 28.5, 0, 75.0, 1500.0, 31.0),
            ("09/02/2026 18:27:06", "WET CYCLE 2 IMPELLER SLOW STOP", 30.6, 63, 75.0, 1500.0, 31.2),
            ("09/02/2026 18:27:06", "WET CYCLE 2 CHOPPER SLOW STOP", 30.6, 63, 75.0, 1500.0, 31.2),
            ("09/02/2026 18:29:09", "WET CYCLE 2 IMPELLER SLOW START", 28.8, 0, 75.0, 1500.0, 31.4),
            ("09/02/2026 18:29:09", "WET CYCLE 2 CHOPPER SLOW START", 28.8, 0, 75.0, 1500.0, 31.4),
            ("09/02/2026 18:30:08", "WET CYCLE 2 IMPELLER SLOW STOP", 30.7, 59, 75.0, 1500.0, 31.5),
            ("09/02/2026 18:30:08", "WET CYCLE 2 CHOPPER SLOW STOP", 30.7, 59, 75.0, 1500.0, 31.5),
            ("09/02/2026 18:31:02", "WET CYCLE 3 IMPELLER SLOW START", 29.0, 0, 75.0, 1500.0, 31.8),
            ("09/02/2026 18:31:02", "WET CYCLE 3 CHOPPER SLOW START", 29.0, 0, 75.0, 1500.0, 31.8),
            ("09/02/2026 18:39:02", "WET CYCLE 3 IMPELLER SLOW STOP", 31.0, 480, 75.0, 1500.0, 32.0),
            ("09/02/2026 18:39:02", "WET CYCLE 3 CHOPPER SLOW STOP", 31.0, 480, 75.0, 1500.0, 32.0),
        ]
        return [
            {
                "DT": _to_iso(t),
                "Time": t,
                "Batch_No": batch_no,
                "Lot_No": lot_no,
                "Status": status,
                "User_Name": user_name,
                "EquipmentCode": dataset_id,
                "EquipmentType": "RMG",
                "Agitator_Speed": ag_spd,
                "Agitator_Current": cur,
                "Granulator_Speed": chp_spd,
                "Granulator_Current": 6.5 if chp_spd > 0 else 0.0,
                "Product_Bed_Temp": p_temp,
                "Duration_Sec": dur,
            }
            for (t, status, cur, dur, ag_spd, chp_spd, p_temp) in rmg_raw
        ]

    # 2. FBD Telemetry
    if family == "FBD":
        fbd_raw = [
            ("09/02/2026 19:30:01", "DRYING START", 27.0, 25.0),
            ("09/02/2026 19:35:01", "DRYING RUNNING", 35.0, 20.0),
            ("09/02/2026 19:48:45", "DRYING RUNNING", 29.0, 23.0),
            ("09/02/2026 19:50:45", "DRYING RUNNING", 48.0, 20.0),
            ("09/02/2026 19:51:21", "DRYING RUNNING", 64.0, 21.0),
            ("09/02/2026 19:55:05", "DRYING RUNNING", 55.0, 22.0),
            ("09/02/2026 20:00:05", "DRYING RUNNING", 56.0, 23.0),
            ("09/02/2026 20:05:05", "DRYING RUNNING", 52.0, 23.0),
            ("09/02/2026 20:10:05", "DRYING RUNNING", 55.0, 23.0),
            ("09/02/2026 20:15:05", "DRYING RUNNING", 51.0, 23.0),
            ("09/02/2026 20:20:05", "DRYING RUNNING", 56.0, 23.0),
            ("09/02/2026 20:25:05", "DRYING RUNNING", 51.0, 23.0),
            ("09/02/2026 20:30:05", "DRYING RUNNING", 55.0, 23.0),
            ("09/02/2026 20:35:05", "DRYING RUNNING", 52.0, 23.0),
            ("09/02/2026 22:40:11", "DRYING RUNNING", 60.0, 30.0),
            ("09/02/2026 22:45:11", "DRYING RUNNING", 63.0, 37.0),
            ("09/02/2026 22:45:11", "DRYING RUNNING", 64.0, 37.0),
            ("09/02/2026 22:45:57", "DRYING RUNNING", 63.0, 37.0),
            ("09/02/2026 22:46:13", "DRYING COMPLETED", 63.0, 37.0),
        ]
        return [
            {
                "DT": _to_iso(t),
                "Time": t,
                "Batch_No": batch_no,
                "Lot_No": lot_no,
                "Status": status,
                "User_Name": user_name,
                "EquipmentCode": dataset_id,
                "EquipmentType": "FBD",
                "Inlet_Temp": in_t,
                "Outlet_Temp": out_t,
            }
            for (t, status, in_t, out_t) in fbd_raw
        ]

    # 3. BLE Telemetry
    if family == "BLE":
        ble_raw = [
            ("11/02/2026 10:21:02", "MIXING 1 STARTED", 5.0),
            ("11/02/2026 10:36:02", "MIXING 1 COMPLETED", 5.0),
            ("11/02/2026 10:55:01", "MIXING 2 STARTED", 5.0),
            ("11/02/2026 11:00:01", "BLENDING OVER", 5.0),
        ]
        return [
            {
                "DT": _to_iso(t),
                "Time": t,
                "Batch_No": batch_no,
                "Lot_No": lot_no,
                "Status": status,
                "User_Name": user_name,
                "EquipmentCode": dataset_id,
                "EquipmentType": "BLE",
                "Blending_Speed": rpm,
            }
            for (t, status, rpm) in ble_raw
        ]

    # 4. COAT Telemetry
    coat_raw = [
        ("12/02/2026 08:35:00", "PRE-HEATING STARTED", 52.0, 38.0, 3.0, 0.0, 0.0),
        ("12/02/2026 08:50:00", "PRE-HEATING COMPLETED", 65.0, 42.0, 3.0, 0.0, 0.0),
        ("12/02/2026 08:55:00", "SPRAYING CYCLE 1 START", 65.0, 43.5, 8.0, 118.0, 2.5),
        ("12/02/2026 09:55:00", "SPRAYING RUNNING", 65.5, 44.0, 8.0, 120.0, 2.5),
        ("12/02/2026 11:55:00", "SPRAYING COMPLETED", 64.8, 44.2, 8.0, 120.0, 2.5),
        ("12/02/2026 12:00:00", "POST DRYING START", 50.0, 41.0, 3.0, 0.0, 0.0),
        ("12/02/2026 12:30:00", "POST DRYING COMPLETED", 48.0, 38.5, 3.0, 0.0, 0.0),
    ]
    return [
        {
            "DT": _to_iso(t),
            "Time": t,
            "Batch_No": batch_no,
            "Lot_No": lot_no,
            "Status": status,
            "User_Name": user_name,
            "EquipmentCode": dataset_id,
            "EquipmentType": "COAT",
            "Inlet_Air_Temp": in_t,
            "Bed_Temp": bed_t,
            "Pan_Speed": pan_spd,
            "Spray_Rate": spray,
            "Atom_Air_Press": atom,
        }
        for (t, status, in_t, bed_t, pan_spd, spray, atom) in coat_raw
    ]


def build_alarm_data(dataset_id: str, from_time: str, to_time: str):
    family = dataset_family(dataset_id)
    if family == "BLE":
        return []  # 0 alarms in BLE.pdf

    if family == "RMG":
        rmg_alarms = [
            ("DISCHARGE VALVE CLOSE FAIL", "09/02/2026 18:47:04", "09/02/2026 19:01:32", "00:14:28", 101),
            ("LID OPENED", "09/02/2026 18:54:45", "09/02/2026 19:01:23", "00:06:38", 102),
            ("DISCHARGE VALVE CLOSE FAIL", "09/02/2026 19:03:08", "09/02/2026 19:03:39", "00:00:31", 103),
        ]
        return [
            {
                "MsgNumber": msg_no,
                "DT": _to_iso(occ),
                "Alarm_Name": name,
                "Occurred_Time": occ,
                "Resolved_Time": res,
                "Duration": dur,
                "MsgText": f"RMG: {name}",
                "EquipmentCode": dataset_id,
                "EquipmentType": "RMG",
            }
            for (name, occ, res, dur, msg_no) in rmg_alarms
        ]

    if family == "COAT":
        coat_alarms = [
            ("SPRAY GUN CHOKED", "12/02/2026 10:14:20", "12/02/2026 10:18:45", "00:04:25", 301),
            ("EXHAUST AIR FLOW LOW", "12/02/2026 11:02:10", "12/02/2026 11:05:00", "00:02:50", 302),
        ]
        return [
            {
                "MsgNumber": msg_no,
                "DT": _to_iso(occ),
                "Alarm_Name": name,
                "Occurred_Time": occ,
                "Resolved_Time": res,
                "Duration": dur,
                "MsgText": f"COAT: {name}",
                "EquipmentCode": dataset_id,
                "EquipmentType": "COAT",
            }
            for (name, occ, res, dur, msg_no) in coat_alarms
        ]

    # FBD
    fbd_alarms = [
        ("PC AIR PRESSURE LOW", "09/02/2026 19:36:03", "09/02/2026 19:48:39", "00:12:36", 201),
        ("EARTH FAULT", "09/02/2026 19:37:15", "09/02/2026 19:48:39", "00:11:24", 202),
        ("INLET TEMP HIGH", "09/02/2026 19:51:21", "09/02/2026 19:55:02", "00:03:41", 203),
        ("PC AIR PRESSURE LOW", "09/02/2026 20:48:40", "09/02/2026 21:01:26", "00:12:46", 204),
        ("EARTH FAULT", "09/02/2026 20:49:48", "09/02/2026 21:01:26", "00:11:38", 205),
        ("INLET TEMP HIGH", "09/02/2026 21:03:55", "09/02/2026 21:07:44", "00:03:49", 206),
        ("PC AIR PRESSURE LOW", "09/02/2026 21:50:45", "09/02/2026 22:00:52", "00:10:07", 207),
        ("EARTH FAULT", "09/02/2026 21:51:51", "09/02/2026 22:00:52", "00:09:01", 208),
        ("INLET TEMP HIGH", "09/02/2026 22:03:26", "09/02/2026 22:06:08", "00:02:42", 209),
        ("PC AIR PRESSURE LOW", "09/02/2026 22:08:31", "09/02/2026 22:34:37", "00:26:06", 210),
        ("EARTH FAULT", "09/02/2026 22:09:34", "09/02/2026 22:34:37", "00:25:03", 211),
        ("INLET TEMP HIGH", "09/02/2026 22:37:13", "09/02/2026 22:39:10", "00:01:57", 212),
        ("INLET TEMP HIGH", "09/02/2026 22:45:11", "09/02/2026 22:45:56", "00:00:45", 213),
        ("PC AIR PRESSURE LOW", "09/02/2026 22:47:01", "09/02/2026 23:25:18", "00:38:17", 214),
        ("EARTH FAULT", "09/02/2026 22:48:04", "09/02/2026 23:25:18", "00:37:14", 215),
    ]
    return [
        {
            "MsgNumber": msg_no,
            "DT": _to_iso(occ),
            "Alarm_Name": name,
            "Occurred_Time": occ,
            "Resolved_Time": res,
            "Duration": dur,
            "MsgText": f"FBD: {name}",
            "EquipmentCode": dataset_id,
            "EquipmentType": "FBD",
        }
        for (name, occ, res, dur, msg_no) in fbd_alarms
    ]


def build_audit_data(dataset_id: str, from_time: str, to_time: str):
    family = dataset_family(dataset_id)
    audit_user = {
        "RMG": "91525 (PB3 RMGC0219 Operator)",
        "FBD": "91525 (PB3 FBDC0220 Operator)",
        "BLE": "91525 (PB3 OCBC0222 Operator)",
        "COAT": "91525 (PB3 COATC0223 Operator)",
    }.get(family, "91525 (PB3 Operator)")

    supervisor_user = {
        "RMG": "91525 (PB3 RMGC0219 Supervisor)",
        "FBD": "91525 (PB3 FBDC0220 Supervisor)",
        "BLE": "91525 (PB3 OCBC0222 Supervisor)",
        "COAT": "91525 (PB3 COATC0223 Supervisor)",
    }.get(family, "91525 (PB3 Supervisor)")

    if family == "RMG":
        rmg_audits = [
            ("09/02/2026 16:04:17", "BATCH START", None, None, None, supervisor_user),
            ("09/02/2026 16:05:36", "PTS START", None, None, None, audit_user),
            ("09/02/2026 16:20:01", "PTS STOP", None, None, None, audit_user),
            ("09/02/2026 18:02:39", "AUTO START", None, None, None, audit_user),
            ("09/02/2026 18:15:28", "ACKNOWLEDGE", None, None, None, audit_user),
            ("09/02/2026 18:22:25", "AUTO START", None, None, None, audit_user),
            ("09/02/2026 18:23:24", "AUTO PAUSE", None, None, "BINDER / GRANULATING AGENT ADDITION", audit_user),
            ("09/02/2026 18:26:02", "AUTO START", None, None, None, audit_user),
            ("09/02/2026 18:27:06", "AUTO PAUSE", None, None, "RAKING / SCRAPPING", audit_user),
            ("09/02/2026 18:29:08", "AUTO START", None, None, None, audit_user),
            ("09/02/2026 18:30:08", "AUTO PAUSE", None, None, "RAKING / SCRAPPING", audit_user),
            ("09/02/2026 18:31:01", "AUTO START", None, None, None, audit_user),
            ("09/02/2026 18:39:03", "AUTO STOP", None, None, None, audit_user),
            ("09/02/2026 18:47:04", "AUTO UNLOAD START", None, None, None, audit_user),
            ("09/02/2026 19:01:32", "ACKNOWLEDGE", None, None, None, audit_user),
            ("09/02/2026 19:05:40", "BATCH END", None, None, None, supervisor_user),
        ]
        return [
            {
                "RecordID": f"AUD-RMG-{idx:02d}",
                "DT": _to_iso(dt),
                "DateTime": dt,
                "TimeStamp": dt,
                "Description": desc,
                "OldValue": old_v or "-",
                "NewValue": new_v or "-",
                "Reason": reason or "-",
                "UserName": user,
                "EquipmentCode": dataset_id,
                "EquipmentType": "RMG",
            }
            for idx, (dt, desc, old_v, new_v, reason, user) in enumerate(rmg_audits, 1)
        ]

    if family == "BLE":
        ble_audits = [
            ("11/02/2026 09:04:55", "BATCH START", None, None, None, supervisor_user),
            ("11/02/2026 09:08:04", "CHARGE START", None, None, None, audit_user),
            ("11/02/2026 10:15:13", "CHARGE STOP", None, None, None, audit_user),
            ("11/02/2026 10:20:52", "BLEND START", None, None, None, audit_user),
            ("11/02/2026 10:21:02", "BLEND START", None, None, None, audit_user),
            ("11/02/2026 10:47:54", "CHARGE START", None, None, None, audit_user),
            ("11/02/2026 10:52:03", "CHARGE STOP", None, None, None, audit_user),
            ("11/02/2026 10:54:12", "BLEND START", None, None, None, audit_user),
            ("11/02/2026 10:55:01", "BLEND START", None, None, None, audit_user),
            ("11/02/2026 11:02:36", "BATCH END", None, None, None, supervisor_user),
        ]
        return [
            {
                "RecordID": f"AUD-BLE-{idx:02d}",
                "DT": _to_iso(dt),
                "DateTime": dt,
                "TimeStamp": dt,
                "Description": desc,
                "OldValue": old_v or "-",
                "NewValue": new_v or "-",
                "Reason": reason or "-",
                "UserName": user,
                "EquipmentCode": dataset_id,
                "EquipmentType": "BLE",
            }
            for idx, (dt, desc, old_v, new_v, reason, user) in enumerate(ble_audits, 1)
        ]

    if family == "COAT":
        coat_audits = [
            ("12/02/2026 08:30:00", "BATCH START", None, None, None, supervisor_user),
            ("12/02/2026 08:35:00", "PRE-HEATING START", None, None, None, audit_user),
            ("12/02/2026 08:55:00", "SPRAYING START", None, None, None, audit_user),
            ("12/02/2026 10:14:20", "AUTO PAUSE", None, None, "SPRAY GUN CLEANING", audit_user),
            ("12/02/2026 10:18:45", "ACKNOWLEDGE", None, None, None, audit_user),
            ("12/02/2026 10:19:00", "AUTO START", None, None, None, audit_user),
            ("12/02/2026 12:00:00", "POST DRYING START", None, None, None, audit_user),
            ("12/02/2026 12:45:30", "BATCH END", None, None, None, supervisor_user),
        ]
        return [
            {
                "RecordID": f"AUD-COAT-{idx:02d}",
                "DT": _to_iso(dt),
                "DateTime": dt,
                "TimeStamp": dt,
                "Description": desc,
                "OldValue": old_v or "-",
                "NewValue": new_v or "-",
                "Reason": reason or "-",
                "UserName": user,
                "EquipmentCode": dataset_id,
                "EquipmentType": "COAT",
            }
            for idx, (dt, desc, old_v, new_v, reason, user) in enumerate(coat_audits, 1)
        ]

    # FBD
    fbd_audits = [
        ("09/02/2026 18:44:45", "BATCH START", None, None, None, supervisor_user),
        ("09/02/2026 18:46:00", "AUTO CHARGING START", None, None, None, audit_user),
        ("09/02/2026 18:53:24", "AUTO CHARGING STOP", None, None, None, audit_user),
        ("09/02/2026 19:01:56", "AUTO CHARGING START", None, None, None, audit_user),
        ("09/02/2026 19:03:53", "AUTO CHARGING STOP", None, None, None, audit_user),
        ("09/02/2026 19:30:01", "AUTO START", None, None, None, audit_user),
        ("09/02/2026 19:35:01", "AUTO STOP", None, None, "RAKING", audit_user),
        ("09/02/2026 19:35:56", "PC SEAL VENT", "ON", "OFF", None, audit_user),
        ("09/02/2026 19:48:35", "PC SEAL VENT", "OFF", "ON", None, audit_user),
        ("09/02/2026 19:48:39", "ACKNOWLEDGE", None, None, None, audit_user),
        ("09/02/2026 19:48:45", "AUTO START", None, None, None, audit_user),
        ("09/02/2026 19:55:02", "ACKNOWLEDGE", None, None, None, audit_user),
        ("09/02/2026 19:55:05", "AUTO START", None, None, None, audit_user),
        ("09/02/2026 20:47:20", "AUTO STOP", None, None, "RAKING", audit_user),
        ("09/02/2026 20:48:34", "PC SEAL VENT", "ON", "OFF", None, audit_user),
        ("09/02/2026 21:01:21", "PC SEAL VENT", "OFF", "ON", None, audit_user),
        ("09/02/2026 21:01:26", "ACKNOWLEDGE", None, None, None, audit_user),
        ("09/02/2026 21:01:28", "AUTO START", None, None, None, audit_user),
    ]
    return [
        {
            "RecordID": f"AUD-FBD-{idx:02d}",
            "DT": _to_iso(dt),
            "DateTime": dt,
            "TimeStamp": dt,
            "Description": desc,
            "OldValue": old_v or "-",
            "NewValue": new_v or "-",
            "Reason": reason or "-",
            "UserName": user,
            "EquipmentCode": dataset_id,
            "EquipmentType": "FBD",
        }
        for idx, (dt, desc, old_v, new_v, reason, user) in enumerate(fbd_audits, 1)
    ]


class MockDataServiceHandler(BaseHTTPRequestHandler):
    """Handles REST GET queries for dataset point names."""

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if not parsed.path.startswith("/fwxapi/rest/v1/Dataset"):
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'{"error": "Not Found"}')
            return

        query_params = parse_qs(parsed.query)
        pointname = query_params.get("pointname", [""])[0]
        if not pointname:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b'{"error": "pointname is required"}')
            return

        dataset_id, dataset_name, params = parse_pointname(pointname)
        payload: object = []

        if dataset_name.upper() == "BATCHDETAILS":
            payload = build_batch_details(dataset_id)
        elif dataset_name.upper() == "PARAMETERSETTINGS":
            payload = build_parameter_settings(
                dataset_id,
                params.get("BATCH_NO", "NL0026008"),
                params.get("LOT_NO", "01 of 05"),
            )
        elif dataset_name.upper() == "BATCHDATA":
            payload = build_batch_data(
                dataset_id,
                params.get("BATCH_NO", "NL0026008"),
                params.get("LOT_NO", "01 of 05"),
            )
        elif dataset_name.upper() == "ALARMDATA":
            payload = build_alarm_data(
                dataset_id,
                params.get("FROMTIME", ""),
                params.get("TOTIME", ""),
            )
        elif dataset_name.upper() == "AUDITDATA":
            payload = build_audit_data(
                dataset_id,
                params.get("FROMTIME", ""),
                params.get("TOTIME", ""),
            )
        else:
            payload = build_batch_data(
                dataset_id,
                params.get("BATCH_NO", "NL0026008"),
                params.get("LOT_NO", "01 of 05"),
            )

        response_payload = {
            "status": "success",
            "pointname": pointname,
            "dataset_id": dataset_id,
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
