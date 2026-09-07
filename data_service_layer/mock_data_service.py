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
        .replace("C0226", "COAT")
        .replace("C0224", "CIP")
    )
    if "RMG" in cleaned:
        return "RMG"
    if "FBD" in cleaned:
        return "FBD"
    if "BLE" in cleaned or "OCB" in cleaned or "OGB" in cleaned:
        return "BLE"
    if "COAT" in cleaned or "COT" in cleaned:
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
            ("09/02/2026 18:02:40", "DRY CYCLE 1 IMPELLER SLOW START", 140.0, 24.5, None, None, 28.5, None),
            ("09/02/2026 18:12:40", "DRY CYCLE 1 IMPELLER SLOW STOP", None, 25.1, None, None, None, 600),
            ("09/02/2026 18:16:03", "WET CYCLE 1 IMPELLER SLOW START", 140.0, 26.2, None, None, 29.5, None),
            ("09/02/2026 18:16:03", "WET CYCLE 1 PUMP 1 START", 140.0, 26.2, None, None, 29.5, None),
            ("09/02/2026 18:18:38", "WET CYCLE 1 IMPELLER SLOW STOP", None, 30.5, None, None, None, 155),
            ("09/02/2026 18:18:38", "WET CYCLE 1 PUMP 1 STOP", None, 30.5, None, None, None, 155),
            ("09/02/2026 18:19:50", "WET CYCLE 1 IMPELLER SLOW START", 140.0, 27.0, None, None, 30.1, None),
            ("09/02/2026 18:19:50", "WET CYCLE 1 PUMP 1 START", 140.0, 27.0, None, None, 30.1, None),
            ("09/02/2026 18:20:15", "WET CYCLE 1 IMPELLER SLOW STOP", None, 30.4, None, None, None, 25),
            ("09/02/2026 18:20:15", "WET CYCLE 1 PUMP 1 STOP", None, 30.4, None, None, None, 25),
            ("09/02/2026 18:22:26", "WET CYCLE 2 IMPELLER SLOW START", 140.0, 28.0, None, None, 30.5, None),
            ("09/02/2026 18:22:26", "WET CYCLE 2 CHOPPER SLOW START", None, None, 1500.0, 6.5, None, None),
            ("09/02/2026 18:23:24", "WET CYCLE 2 IMPELLER SLOW STOP", None, 30.6, None, None, None, 58),
            ("09/02/2026 18:23:24", "WET CYCLE 2 CHOPPER SLOW STOP", None, None, None, 6.5, None, 58),
            ("09/02/2026 18:26:03", "WET CYCLE 2 IMPELLER SLOW START", 140.0, 28.5, None, None, 31.0, None),
            ("09/02/2026 18:26:03", "WET CYCLE 2 CHOPPER SLOW START", None, None, 1500.0, 6.5, None, None),
            ("09/02/2026 18:27:06", "WET CYCLE 2 IMPELLER SLOW STOP", None, 30.6, None, None, None, 63),
            ("09/02/2026 18:27:06", "WET CYCLE 2 CHOPPER SLOW STOP", None, None, None, 6.5, None, 63),
            ("09/02/2026 18:29:09", "WET CYCLE 2 IMPELLER SLOW START", 140.0, 28.8, None, None, 31.4, None),
            ("09/02/2026 18:29:09", "WET CYCLE 2 CHOPPER SLOW START", None, None, 1500.0, 6.5, None, None),
            ("09/02/2026 18:30:08", "WET CYCLE 2 IMPELLER SLOW STOP", None, 30.7, None, None, None, 59),
            ("09/02/2026 18:30:08", "WET CYCLE 2 CHOPPER SLOW STOP", None, None, None, 6.5, None, 59),
            ("09/02/2026 18:31:02", "WET CYCLE 3 IMPELLER SLOW START", 140.0, 29.0, None, None, 31.8, None),
            ("09/02/2026 18:31:02", "WET CYCLE 3 CHOPPER SLOW START", None, None, 1500.0, 6.5, None, None),
            ("09/02/2026 18:39:02", "WET CYCLE 3 IMPELLER SLOW STOP", None, 31.0, None, None, None, 480),
            ("09/02/2026 18:39:02", "WET CYCLE 3 CHOPPER SLOW STOP", None, None, None, 6.6, None, 480),
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
                "Agitator_Current": ag_cur,
                "Granulator_Speed": chp_spd,
                "Granulator_Current": chp_cur,
                "Granulation_Temperature": p_temp,
                "Duration_Sec": dur,
            }
            for (t, status, ag_spd, ag_cur, chp_spd, chp_cur, p_temp, dur) in rmg_raw
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
            ("INLET AIR TEMP HIGH", "23/02/2026 12:14:46", "23/02/2026 12:14:58", "00:00:12", 301),
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
        ("PC AIR PRESSURE LOW", "08/02/2026 18:43:46", "-", "-", 201),
        ("EARTH FAULT", "08/02/2026 18:44:55", "-", "-", 202),
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
        rmg_sup = "91525 (PB3 RMGC0219 Supervisor)"
        rmg_op = "8961 (PB3 RMGC0219 Operator)"
        rmg_audits = [
            ("09/02/2026 16:04:17", "BATCH START", None, None, None, rmg_sup),
            ("09/02/2026 16:05:36", "PTS START", None, None, None, rmg_op),
            ("09/02/2026 16:20:01", "PTS STOP", None, None, None, rmg_op),
            ("09/02/2026 18:02:39", "AUTO START", None, None, None, rmg_op),
            ("09/02/2026 18:15:28", "ACKNOWLEDGE", None, None, None, rmg_op),
            ("09/02/2026 18:16:02", "AUTO START", None, None, None, rmg_op),
            ("09/02/2026 18:18:38", "AUTO PAUSE", None, None, None, rmg_op),
            ("09/02/2026 18:18:41", "AUTO PAUSE REASON", None, None, "BINDER/GRANULATING AGENT ADDITION", rmg_op),
            ("09/02/2026 18:19:49", "AUTO CONTINUE", None, None, None, rmg_op),
            ("09/02/2026 18:20:23", "ACKNOWLEDGE", None, None, None, rmg_op),
            ("09/02/2026 18:22:25", "AUTO START", None, None, None, rmg_op),
            ("09/02/2026 18:23:24", "AUTO PAUSE", None, None, None, rmg_op),
            ("09/02/2026 18:23:27", "AUTO PAUSE REASON", None, None, "BINDER/GRANULATING AGENT ADDITION", rmg_op),
            ("09/02/2026 18:26:02", "AUTO CONTINUE", None, None, None, rmg_op),
            ("09/02/2026 18:27:06", "AUTO PAUSE", None, None, None, rmg_op),
            ("09/02/2026 18:27:09", "AUTO PAUSE REASON", None, None, "BINDER/GRANULATING AGENT ADDITION", rmg_op),
            ("09/02/2026 18:29:08", "AUTO CONTINUE", None, None, None, rmg_op),
            ("09/02/2026 18:30:16", "ACKNOWLEDGE", None, None, None, rmg_op),
            ("09/02/2026 18:31:01", "AUTO START", None, None, None, rmg_op),
            ("09/02/2026 18:39:13", "ACKNOWLEDGE", None, None, None, rmg_op),
            ("09/02/2026 18:47:02", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:47:07", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:47:21", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:47:25", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:47:36", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:47:41", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:47:52", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:47:58", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:48:11", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:48:16", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:48:27", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:48:33", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:48:45", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:48:50", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:49:04", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:49:09", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:49:22", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:49:27", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:49:41", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:49:46", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:50:00", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:50:06", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:50:19", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:50:25", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:50:37", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:50:43", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:50:57", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:51:03", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:51:17", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:51:23", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:51:37", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:51:42", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:51:53", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:51:59", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:52:10", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:52:16", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:52:25", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:52:37", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:52:51", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 18:53:13", "AUTO UNLOAD STOP", None, None, "RACKING/SCRAPPING", rmg_op),
            ("09/02/2026 18:54:41", "LID OPEN", None, None, None, rmg_op),
            ("09/02/2026 19:01:00", "LID CLOSE", None, None, None, rmg_op),
            ("09/02/2026 19:01:32", "ACKNOWLEDGE", None, None, None, rmg_op),
            ("09/02/2026 19:03:06", "AUTO UNLOAD START", None, None, None, rmg_op),
            ("09/02/2026 19:03:30", "AUTO UNLOAD STOP", None, None, "PROCESS OVER", rmg_op),
            ("09/02/2026 19:03:39", "ACKNOWLEDGE", None, None, None, rmg_op),
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
        ble_sup = "91525 (PB3 OCBC0222 Supervisor)"
        ble_op = "25081 (PB3 OCBC0222 Operator)"
        ble_audits = [
            ("11/02/2026 09:04:55", "BATCH START", None, None, None, ble_sup),
            ("11/02/2026 09:08:04", "CHARGE START", None, None, None, ble_op),
            ("11/02/2026 10:15:13", "CHARGE STOP", None, None, None, ble_op),
            ("11/02/2026 10:20:52", "BLEND START", None, None, None, ble_op),
            ("11/02/2026 10:21:02", "BLEND START", None, None, None, ble_op),
            ("11/02/2026 10:47:54", "CHARGE START", None, None, None, ble_op),
            ("11/02/2026 10:52:03", "CHARGE STOP", None, None, None, ble_op),
            ("11/02/2026 10:54:12", "BLEND START", None, None, None, ble_op),
            ("11/02/2026 10:55:01", "BLEND START", None, None, None, ble_op),
            ("11/02/2026 11:02:36", "BATCH END", None, None, None, ble_sup),
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
            ('23/02/2026 11:36:50', 'BATCH START', None, None, None, '98204 (PB3 COTC0226 Supervisor)'),
            ('23/02/2026 11:37:49', 'RETRACTABLE ARM OUT', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:38:09', 'TABLET LOADING START', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:39:17', 'EXHAUST DAMPER OPENING', '60.0', '40.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:53:24', 'CONTROL PANEL CONDENSATE SET', '80', '319', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:53:32', 'TABLET LOADING END', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:55:34', 'DE DUSTING START', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:56:34', 'DE DUSTING OVER', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:56:44', 'DOSING', 'OFF', 'ON', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:56:58', 'MANUAL MODE DOSING PUMP RPM', '25.0', '16.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:57:11', 'GUN VALIDATION', 'OFF', 'ON', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 11:58:11', 'GUN VALIDATION', 'ON', 'OFF', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:01:04', 'GUN VALIDATION', 'OFF', 'ON', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:02:04', 'GUN VALIDATION', 'ON', 'OFF', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:08:43', 'GUN VALIDATION', 'OFF', 'ON', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:09:43', 'GUN VALIDATION', 'ON', 'OFF', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:12:09', 'DOSING PUMP START', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:12:12', 'DOSING PUMP STOP', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:12:16', 'DOSING PUMP STOP', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:12:33', 'RETRACTABLE ARM IN', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:13:14', 'MACHNE MODE AUTO', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:13:20', 'DOSING', 'ON', 'OFF', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:13:23', 'COATING START', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:14:53', 'EXHAUST DAMPER OPENING', '40.0', '100.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:14:57', 'INLET DAMPER OPENING', '95.0', '70.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:19:23', 'PRE JOG STARTED', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:29:23', 'PRE JOG OVER', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:35:07', 'CONDENSATE', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:43:08', 'CONDENSATE', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:46:24', 'AGITATOR SOLUTION', 'OFF', 'ON', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:50:44', 'CONTROL PANEL CONDENSATE SET', '319', '60', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:55:39', 'CONTROL PANEL CONDENSATE SET', '60', '100', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:56:41', 'CONTROL PANEL CONDENSATE SET', '100', '10', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:56:46', 'DOSING', 'OFF', 'ON', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 12:58:01', 'DOSING PUMP SET SPEED', '18.0', '17.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 13:37:13', 'PAN SPEED', '2.5', '3.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 13:55:17', 'PAN SPEED', '3.0', '3.5', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 14:40:28', 'PAN SPEED', '3.5', '4.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 15:30:37', 'DOSING PUMP SET SPEED', '17.0', '15.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 15:30:46', 'INLET DAMPER OPENING', '70.0', '60.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:01:42', 'PAN SPEED', '4.0', '5.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:01:50', 'DOSING PUMP SET SPEED', '15.0', '14.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:01:56', 'CONTROL PANEL CONDENSATE SET', '10', '1', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:02:08', 'CONTROL PANEL CONDENSATE SET', '1', '60', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:53:13', 'CONTROL PANEL CONDENSATE SET', '60', '10', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:53:23', 'PAN SPEED', '5.0', '4.5', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:53:28', 'DOSING PUMP SET SPEED', '14.0', '12.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:53:30', 'PAN SPEED', '4.5', '4.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:53:37', 'DOSING PUMP SET SPEED', '12.0', '11.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:54:13', 'DOSING PUMP SET SPEED', '11.0', '10.5', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:54:26', 'INLET DAMPER OPENING', '60.0', '50.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 16:55:00', 'PAN SPEED', '4.0', '3.5', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:26:04', 'DOSING', 'ON', 'OFF', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:26:08', 'AUTO STOP', None, None, 'TABLET BUILD UP WEIGHT REACHED', '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:26:27', 'AGITATOR SOLUTION', 'ON', 'OFF', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:28:03', 'POST JOG ON/OFF', 'OFF', 'ON', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:28:23', 'POST JOG STARTED', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:38:23', 'POST JOG OVER', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:38:45', 'POST JOG ON/OFF', 'ON', 'OFF', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:41:46', 'RETRACTABLE ARM OUT', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:42:03', 'MACHNE MODE MANUAL', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:42:12', 'EXHAUST DAMPER OPENING', '100.0', '50.0', None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 17:42:18', 'EXHAUST BLOWER START', None, None, None, '24159 (PB3 COTC0226 Operator)'),
            ('23/02/2026 18:10:49', 'PAN MOTOR START', None, None, None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 18:10:55', 'PAN MOTOR STOP', None, None, None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 18:46:08', 'EXHAUST BLOWER STOP', None, None, None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 18:46:19', 'UNLOADING START', None, None, None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 18:46:33', 'EXHAUST DAMPER OPENING', '50.0', '40.0', None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 18:46:41', 'MANUAL MODE PAN MOTOR RPM', '1.0', '2.0', None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 19:02:47', 'PAN PAUSE', None, None, None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 19:04:57', 'PAN CONTINUE', None, None, None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 19:15:56', 'UNLOADING END', None, None, None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 19:19:31', 'RETRACTABLE ARM IN', None, None, None, '28780 (PB3 COTC0226 Operator)'),
            ('23/02/2026 19:53:08', 'BATCH END', None, None, None, '99728 (PB3 COTC0226 Supervisor)'),
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
    fbd_sup = "98204 (PB3 FBDC0220 Supervisor)"
    fbd_op1 = "8961 (PB3 FBDC0220 Operator)"
    fbd_op2 = "96599 (PB3 FBDC0220 Operator)"
    fbd_audits = [
        ("09/02/2026 18:44:45", "BATCH START", None, None, None, fbd_sup),
        ("09/02/2026 18:46:00", "AUTO CHARGING START", None, None, None, fbd_op1),
        ("09/02/2026 18:53:24", "AUTO CHARGING STOP", None, None, None, fbd_op1),
        ("09/02/2026 19:01:56", "AUTO CHARGING START", None, None, None, fbd_op1),
        ("09/02/2026 19:03:53", "AUTO CHARGING STOP", None, None, None, fbd_op1),
        ("09/02/2026 19:30:01", "AUTO START", None, None, None, fbd_op1),
        ("09/02/2026 19:35:01", "AUTO STOP", None, None, "RAKING", fbd_op1),
        ("09/02/2026 19:35:56", "PC SEAL VENT", "ON", "OFF", None, fbd_op1),
        ("09/02/2026 19:48:35", "PC SEAL VENT", "OFF", "ON", None, fbd_op1),
        ("09/02/2026 19:48:39", "ACKNOWLEDGE", None, None, None, fbd_op1),
        ("09/02/2026 19:48:45", "AUTO START", None, None, None, fbd_op1),
        ("09/02/2026 19:55:02", "ACKNOWLEDGE", None, None, None, fbd_op1),
        ("09/02/2026 19:55:05", "AUTO START", None, None, None, fbd_op1),
        ("09/02/2026 20:47:20", "AUTO STOP", None, None, "RAKING", fbd_op1),
        ("09/02/2026 20:48:34", "PC SEAL VENT", "ON", "OFF", None, fbd_op1),
        ("09/02/2026 21:01:21", "PC SEAL VENT", "OFF", "ON", None, fbd_op1),
        ("09/02/2026 21:01:26", "ACKNOWLEDGE", None, None, None, fbd_op1),
        ("09/02/2026 21:01:28", "AUTO START", None, None, None, fbd_op1),
        ("09/02/2026 21:07:44", "ACKNOWLEDGE", None, None, None, fbd_op1),
        ("09/02/2026 21:07:45", "AUTO START", None, None, None, fbd_op1),
        ("09/02/2026 21:49:31", "AUTO STOP", None, None, "RAKING", fbd_op1),
        ("09/02/2026 21:50:39", "PC SEAL VENT", "ON", "OFF", None, fbd_op1),
        ("09/02/2026 22:00:50", "PC SEAL VENT", "OFF", "ON", None, fbd_op1),
        ("09/02/2026 22:00:52", "ACKNOWLEDGE", None, None, None, fbd_op1),
        ("09/02/2026 22:01:01", "AUTO START", None, None, None, fbd_op1),
        ("09/02/2026 22:06:08", "ACKNOWLEDGE", None, None, None, fbd_op2),
        ("09/02/2026 22:06:09", "AUTO START", None, None, None, fbd_op2),
        ("09/02/2026 22:07:30", "AUTO STOP", None, None, "LOD CHECK", fbd_op2),
        ("09/02/2026 22:08:24", "PC SEAL VENT", "ON", "OFF", None, fbd_op2),
        ("09/02/2026 22:34:33", "PC SEAL VENT", "OFF", "ON", None, fbd_op2),
        ("09/02/2026 22:34:37", "ACKNOWLEDGE", None, None, None, fbd_op2),
        ("09/02/2026 22:34:38", "AUTO START", None, None, None, fbd_op2),
        ("09/02/2026 22:39:10", "ACKNOWLEDGE", None, None, None, fbd_op2),
        ("09/02/2026 22:39:11", "AUTO START", None, None, None, fbd_op2),
        ("09/02/2026 22:45:56", "ACKNOWLEDGE", None, None, None, fbd_op2),
        ("09/02/2026 22:45:57", "AUTO START", None, None, None, fbd_op2),
        ("09/02/2026 22:46:13", "AUTO STOP", None, None, "LOD CHECK", fbd_op2),
        ("09/02/2026 22:46:54", "PC SEAL VENT", "ON", "OFF", None, fbd_op2),
        ("09/02/2026 23:25:13", "PC SEAL VENT", "OFF", "ON", None, fbd_op2),
        ("09/02/2026 23:25:18", "ACKNOWLEDGE", None, None, None, fbd_op2),
        ("09/02/2026 23:26:01", "AUTO DISCHARGE START", None, None, None, fbd_op2),
        ("09/02/2026 23:36:01", "AUTO DISCHARGE STOP", None, None, None, fbd_op2),
        ("09/02/2026 23:37:03", "AUTO DISCHARGE START", None, None, None, fbd_op2),
        ("09/02/2026 23:45:01", "AUTO DISCHARGE STOP", None, None, None, fbd_op2),
        ("09/02/2026 23:47:01", "BATCH END", None, None, None, fbd_sup),
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
