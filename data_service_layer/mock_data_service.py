#!/usr/bin/env python3
"""Mock data service that mimics the source API used by the scheduler.

This temporary service is designed to match the endpoint pattern defined in:
- data_service_layer/script_requirement.md
- scheduler/requirement.md

It exposes sample data for:
- BATCHDETAILS
- BATCHDATA
- ALARMDATA
- AUDITDATA

It is intentionally simple and dependency-free so that the rest of the application
can be implemented against a stable contract before the real external API is wired in.
"""

from __future__ import annotations

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST = "0.0.0.0"
PORT = 8000


def dataset_family(dataset_id: str) -> str:
    return (dataset_id or "G5RMG")[-3:].upper()


def dataset_number(dataset_id: str) -> int:
    text = str(dataset_id or "")
    digits = "".join(ch for ch in text if ch.isdigit())
    return int(digits[-1]) if digits else 5


def parse_pointname(pointname: str):
    """Return the dataset id, dataset name, and parameter map from a source pointname string.

    Examples:
        db:G5RMG.BATCHDATA<@BATCH_NO='VI0026050', @LOT_NO='01 of 05 STEP-1'>
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
    # Keep product code and batch number common across RMG/FBD/OGB.
    common_specs = [
        ("Allopurinol tablets", "STAPU1000", "UB0026002"),
        ("Levetiracetam tablets", "STLEV5000", "LV0026001"),
    ]

    details = []
    for product_name, product_code, batch_no in common_specs:
        for lot_index in range(1, 6):
            details.append(
                {
                    "PRODUCT_NAME": product_name,
                    "PRODUCT_CODE": product_code,
                    "BATCH_NO": batch_no,
                    "LOT_NO": f"{lot_index:02d} of 05",
                }
            )

    return details


def build_batch_data(dataset_id: str, batch_no: str, lot_no: str):
    family = dataset_family(dataset_id)
    line = dataset_number(dataset_id)

    lot_prefix = str(lot_no or "").strip().split(" ")[0]
    try:
        lot_index = int(lot_prefix)
    except ValueError:
        lot_index = 1

    family_profiles = {
        "RMG": {
            "user": "44126",
            "temp_base": 3277,
            "status_flow": [
                "Batch Started",
                "Auto Cycle Started",
                "Dry Mix Started",
                "Dry Mix Over",
                "Wet Mix Started",
                "Wet Mix Running",
                "Batch Completed",
            ],
        },
        "FBD": {
            "user": "45210",
            "temp_base": 2850,
            "status_flow": [
                "Batch Started",
                "Tray Loading Started",
                "Drying Started",
                "Drying Running",
                "Cooling Started",
                "Cooling Running",
                "Batch Completed",
            ],
        },
        "OGB": {
            "user": "46890",
            "temp_base": 3010,
            "status_flow": [
                "Batch Started",
                "Granulation Started",
                "Binder Addition Started",
                "Granulation Running",
                "Sizing Started",
                "Sizing Running",
                "Batch Completed",
            ],
        },
    }
    profile = family_profiles.get(family, family_profiles["RMG"])
    status_flow = profile["status_flow"]
    temp_base = profile["temp_base"] + line
    user_name = profile["user"]

    batch_variation_seed = sum(ord(ch) for ch in str(batch_no or "")) % 11
    lot_variation_seed = lot_index

    if family in {"FBD", "OGB"}:
        # Enforce sequential lot progression for dryer/granulation families:
        # when the current active lot is in progress, downstream lots have not started yet.
        active_lot_index = max(1, min(5, line - 2))
        if lot_index > active_lot_index:
            return []
        include_completed = lot_index < active_lot_index
    else:
        include_completed = True

    final_status = status_flow[6] if include_completed else status_flow[5]

    timeline = [
        ("2026-08-13T06:57:45.673", 0, 0),
        ("2026-08-13T06:57:50.200", 0, 1),
        ("2026-08-13T06:57:51.183", 0, 2),
        ("2026-08-13T07:07:51.000", 305, 3),
        ("2026-08-13T07:12:15.000", 520, 4),
        ("2026-08-13T07:15:00.000", 840, 5),
        ("2026-08-13T07:20:00.000", 1340, 6),
    ]

    family_telemetry = {
        "RMG": [
            {"Ag_Amps": 0, "Ag_Speed": 0, "Chp_Amps": 0, "Chp_Speed": 0, "Dosing_Speed": 0, "WMD_Speed": 0, "Heater_Temp": temp_base},
            {"Ag_Amps": 0, "Ag_Speed": 0, "Chp_Amps": 0, "Chp_Speed": 0, "Dosing_Speed": 0, "WMD_Speed": 0, "Heater_Temp": temp_base},
            {"Ag_Amps": 0, "Ag_Speed": 0, "Chp_Amps": 0, "Chp_Speed": 0, "Dosing_Speed": 0, "WMD_Speed": 0, "Heater_Temp": temp_base},
            {"Ag_Amps": 12.3, "Ag_Speed": 30.4, "Chp_Amps": 15.2, "Chp_Speed": 47.5, "Dosing_Speed": 25.6, "WMD_Speed": 18.7, "Heater_Temp": temp_base + 24},
            {"Ag_Amps": 15.8, "Ag_Speed": 33.9, "Chp_Amps": 16.1, "Chp_Speed": 49.6, "Dosing_Speed": 29.9, "WMD_Speed": 20.1, "Heater_Temp": temp_base + 38},
            {"Ag_Amps": 18.6, "Ag_Speed": 36.0, "Chp_Amps": 17.9, "Chp_Speed": 52.1, "Dosing_Speed": 31.7, "WMD_Speed": 22.5, "Heater_Temp": temp_base + 53},
            {"Ag_Amps": 19.3, "Ag_Speed": 38.2, "Chp_Amps": 18.5, "Chp_Speed": 53.4, "Dosing_Speed": 32.1, "WMD_Speed": 23.8, "Heater_Temp": temp_base + 68},
        ],
        "FBD": [
            {"Inlet_Temp": temp_base - 20, "Outlet_Temp": temp_base - 35, "Bed_Temp": temp_base - 45, "Blower_Amps": 0, "Exhaust_Fan_Speed": 0, "Damper_Position": 0, "Steam_Pressure": 0},
            {"Inlet_Temp": temp_base - 18, "Outlet_Temp": temp_base - 33, "Bed_Temp": temp_base - 42, "Blower_Amps": 6.2, "Exhaust_Fan_Speed": 18.0, "Damper_Position": 20, "Steam_Pressure": 0.8},
            {"Inlet_Temp": temp_base - 8, "Outlet_Temp": temp_base - 22, "Bed_Temp": temp_base - 29, "Blower_Amps": 9.5, "Exhaust_Fan_Speed": 34.0, "Damper_Position": 40, "Steam_Pressure": 1.4},
            {"Inlet_Temp": temp_base + 6, "Outlet_Temp": temp_base - 6, "Bed_Temp": temp_base - 14, "Blower_Amps": 12.8, "Exhaust_Fan_Speed": 52.0, "Damper_Position": 65, "Steam_Pressure": 2.1},
            {"Inlet_Temp": temp_base + 12, "Outlet_Temp": temp_base + 2, "Bed_Temp": temp_base - 4, "Blower_Amps": 11.1, "Exhaust_Fan_Speed": 46.0, "Damper_Position": 55, "Steam_Pressure": 1.6},
            {"Inlet_Temp": temp_base + 4, "Outlet_Temp": temp_base - 4, "Bed_Temp": temp_base - 10, "Blower_Amps": 8.6, "Exhaust_Fan_Speed": 38.0, "Damper_Position": 48, "Steam_Pressure": 1.0},
            {"Inlet_Temp": temp_base - 4, "Outlet_Temp": temp_base - 14, "Bed_Temp": temp_base - 18, "Blower_Amps": 5.2, "Exhaust_Fan_Speed": 24.0, "Damper_Position": 30, "Steam_Pressure": 0.5},
        ],
        "OGB": [
            {"Impeller_Speed": 0, "Impeller_Amps": 0, "Chopper_Speed": 0, "Chopper_Amps": 0, "Binder_Flow": 0, "Granule_Moisture": 0, "Discharge_Gate_Position": 0},
            {"Impeller_Speed": 22.0, "Impeller_Amps": 7.2, "Chopper_Speed": 18.0, "Chopper_Amps": 4.4, "Binder_Flow": 0, "Granule_Moisture": 5.1, "Discharge_Gate_Position": 5},
            {"Impeller_Speed": 30.0, "Impeller_Amps": 9.6, "Chopper_Speed": 26.0, "Chopper_Amps": 6.8, "Binder_Flow": 12.5, "Granule_Moisture": 8.7, "Discharge_Gate_Position": 8},
            {"Impeller_Speed": 37.0, "Impeller_Amps": 11.2, "Chopper_Speed": 33.0, "Chopper_Amps": 8.1, "Binder_Flow": 16.8, "Granule_Moisture": 12.9, "Discharge_Gate_Position": 12},
            {"Impeller_Speed": 28.0, "Impeller_Amps": 8.5, "Chopper_Speed": 24.0, "Chopper_Amps": 6.2, "Binder_Flow": 6.4, "Granule_Moisture": 10.1, "Discharge_Gate_Position": 40},
            {"Impeller_Speed": 20.0, "Impeller_Amps": 6.4, "Chopper_Speed": 16.0, "Chopper_Amps": 4.8, "Binder_Flow": 0.0, "Granule_Moisture": 7.2, "Discharge_Gate_Position": 70},
            {"Impeller_Speed": 10.0, "Impeller_Amps": 3.1, "Chopper_Speed": 8.0, "Chopper_Amps": 2.3, "Binder_Flow": 0.0, "Granule_Moisture": 4.2, "Discharge_Gate_Position": 100},
        ],
    }

    telemetry_rows = family_telemetry.get(family, family_telemetry["RMG"])

    def with_variation(metrics: dict[str, object], idx: int) -> dict[str, object]:
        """Apply deterministic variation so batch/lot values are not identical across loads."""
        batch_scale = (batch_variation_seed * 0.07) + (lot_variation_seed * 0.05) + (line * 0.03)
        row_scale = idx * 0.02

        adjusted: dict[str, object] = {}
        for key, value in metrics.items():
            if isinstance(value, (int, float)):
                adjusted_value = float(value) + batch_scale + row_scale
                adjusted[key] = round(adjusted_value, 2)
            else:
                adjusted[key] = value
        return adjusted

    rows: list[dict[str, object]] = []

    for idx, (dt_value, time_value, status_idx) in enumerate(timeline):
        stage_status = final_status if idx == len(timeline) - 1 else status_flow[status_idx]
        row = {
            "DT": dt_value,
            "Batch_No": batch_no,
            "Lot_No": lot_no,
            "Time": time_value,
            "Status": stage_status,
            "User_Name": user_name,
            "EquipmentCode": dataset_id,
            "EquipmentType": family,
        }
        row.update(with_variation(telemetry_rows[idx], idx))
        rows.append(row)

    return rows


def build_alarm_data(dataset_id: str, from_time: str, to_time: str):
    family = dataset_family(dataset_id)
    line = dataset_number(dataset_id)
    family_msg = {
        "RMG": ("Wet Mix Step 4 Over", "High temperature", "Wet Mix Started"),
        "FBD": ("Drying Stage 4 Over", "Inlet temperature high", "Cooling Started"),
        "OGB": ("Granulation Stage 4 Over", "Binder flow high", "Sizing Started"),
    }
    msg1, msg2, msg3 = family_msg.get(family, family_msg["RMG"])
    msg_base = 200 + (line * 10)

    return [
        {
            "Time_ms": 46247556652.44213 + (line * 100),
            "MsgProc": 2,
            "StateAfter": 0,
            "MsgClass": 64,
            "MsgNumber": msg_base + 12,
            "Var1": "                                                                                                                                                                                                                                                               ",
            "Var2": "                                                                                                                                                                                                                                                               ",
            "Var3": "                                                                                                                                                                                                                                                               ",
            "Var4": "                                                                                                                                                                                                                                                               ",
            "Var5": "                                                                                                                                                                                                                                                               ",
            "Var6": "                                                                                                                                                                                                                                                               ",
            "Var7": "                                                                                                                                                                                                                                                               ",
            "Var8": "                                                                                                                                                                                                                                                               ",
            "TimeString": "13/08/2026 13:21:35       ",
            "MsgText": f"{family}: {msg1}",
            "PLC": f"HMI_Connection_{line}",
            "DT": "2026-08-13T13:21:35",
            "EquipmentCode": dataset_id,
            "EquipmentType": family,
        },
        {
            "Time_ms": 46247484200.0 + (line * 100),
            "MsgProc": 2,
            "StateAfter": 1,
            "MsgClass": 64,
            "MsgNumber": msg_base + 1,
            "Var1": msg2,
            "Var2": "",
            "Var3": "",
            "Var4": "",
            "Var5": "",
            "Var6": "",
            "Var7": "",
            "Var8": "",
            "TimeString": "13/08/2026 07:07:51",
            "MsgText": f"{family}: {msg2}",
            "PLC": f"HMI_Connection_{line}",
            "DT": "2026-08-13T07:07:51",
            "EquipmentCode": dataset_id,
            "EquipmentType": family,
        },
        {
            "Time_ms": 46247510000.0 + (line * 100),
            "MsgProc": 2,
            "StateAfter": 1,
            "MsgClass": 64,
            "MsgNumber": msg_base + 2,
            "Var1": msg3,
            "Var2": "",
            "Var3": "",
            "Var4": "",
            "Var5": "",
            "Var6": "",
            "Var7": "",
            "Var8": "",
            "TimeString": "13/08/2026 07:12:15",
            "MsgText": f"{family}: {msg3}",
            "PLC": f"HMI_Connection_{line}",
            "DT": "2026-08-13T07:12:15",
            "EquipmentCode": dataset_id,
            "EquipmentType": family,
        },
    ]


def build_audit_data(dataset_id: str, from_time: str, to_time: str):
    family = dataset_family(dataset_id)
    line = dataset_number(dataset_id)
    audit_user = {"RMG": "44126", "FBD": "45210", "OGB": "46890"}.get(family, "44126")
    object_prefix = {"RMG": "Recipe: RMG", "FBD": "Recipe: FBD", "OGB": "Recipe: OGB"}.get(family, "Recipe: RMG")
    description_prefix = {
        "RMG": "Mixer synchronization completed",
        "FBD": "Drying synchronization completed",
        "OGB": "Granulation synchronization completed",
    }.get(family, "Mixer synchronization completed")
    record_base = 95370 + (line * 100)

    return [
        {
            "RecordID": record_base + 9,
            "TimeStamp": "13/08/2026 06:57:45",
            "DeltaToUTC": "\"-5:30\"",
            "UserID": audit_user,
            "ObjectID": object_prefix,
            "Description": f"{description_prefix} - start acknowledgement.",
            "Comment": None,
            "Checksum": "YcrA30",
            "DT": "2026-08-13T06:57:45",
            "EquipmentCode": dataset_id,
            "EquipmentType": family,
        },
        {
            "RecordID": record_base + 10,
            "TimeStamp": "13/08/2026 06:57:50",
            "DeltaToUTC": "\"-5:30\"",
            "UserID": audit_user,
            "ObjectID": f"{object_prefix} - Stage Trigger",
            "Description": f"{description_prefix} - stage trigger updated.",
            "Comment": None,
            "Checksum": "Uk82de",
            "DT": "2026-08-13T06:57:50",
            "EquipmentCode": dataset_id,
            "EquipmentType": family,
        },
        {
            "RecordID": record_base + 11,
            "TimeStamp": "13/08/2026 07:08:24",
            "DeltaToUTC": "\"-5:30\"",
            "UserID": audit_user,
            "ObjectID": f"{object_prefix} - Stage Continue",
            "Description": f"{description_prefix} - continue acknowledgement.",
            "Comment": None,
            "Checksum": "4PIAQk",
            "DT": "2026-08-13T07:08:24",
            "EquipmentCode": dataset_id,
            "EquipmentType": family,
        },
    ]


class MockDataServiceHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlparse(self.path)
        query_params = parse_qs(url.query)
        pointname = query_params.get("pointname", [""])[0]

        dataset_id, dataset_name, params = parse_pointname(pointname)

        if dataset_name == "BATCHDETAILS":
            payload = build_batch_details(dataset_id)
        elif dataset_name == "BATCHDATA":
            batch_no = params.get("BATCH_NO") or "VI0026050"
            lot_no = params.get("LOT_NO") or "01 of 05 STEP-1"
            payload = build_batch_data(dataset_id, batch_no, lot_no)
        elif dataset_name == "ALARMDATA":
            from_time = params.get("FROMTIME") or "2026-08-13 06:57:45"
            to_time = params.get("TOTIME") or "2026-08-13 13:39:54"
            payload = build_alarm_data(dataset_id, from_time, to_time)
        elif dataset_name == "AUDITDATA":
            from_time = params.get("FROMTIME") or "2026-08-13 06:57:45"
            to_time = params.get("TOTIME") or "2026-08-13 13:39:54"
            payload = build_audit_data(dataset_id, from_time, to_time)
        else:
            payload = {
                "error": "Unknown dataset",
                "pointname": pointname,
            }

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
        # Keep the console output clean during local development.
        return


if __name__ == "__main__":
    if len(sys.argv) > 1:
        try:
            PORT = int(sys.argv[1])
        except ValueError as exc:
            raise SystemExit(f"Invalid port value: {sys.argv[1]}") from exc

    server = ThreadingHTTPServer((HOST, PORT), MockDataServiceHandler)
    print(f"Mock Data Service running on http://{HOST}:{PORT}")
    print("Endpoints:")
    print("  /fwxapi/rest/v1/Dataset?pointname=db:G5RMG.BATCHDETAILS")
    print("  /fwxapi/rest/v1/Dataset?pointname=db:G5RMG.BATCHDATA<@BATCH_NO='VI0026050', @LOT_NO='01 of 05 STEP-1'>")
    print("  /fwxapi/rest/v1/Dataset?pointname=db:G5RMG.ALARMDATA<@FROMTIME='2026-08-13 06:57:45', @TOTIME='2026-08-13 13:39:54'>")
    print("  /fwxapi/rest/v1/Dataset?pointname=db:G5RMG.AUDITDATA<@FROMTIME='2026-08-13 06:57:45', @TOTIME='2026-08-13 13:39:54'>")
    server.serve_forever()
