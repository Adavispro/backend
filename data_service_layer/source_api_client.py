#!/usr/bin/env python3
"""Client utilities for retrieving source batch, alarm, and audit data.

This is the first step in the ingestion pipeline. It talks to a mock source API
that mimics the production system until the real API is connected.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from datetime import datetime
from typing import Any, Dict, List, Optional
from urllib.parse import quote

import requests


BASE_URL = "http://localhost:8000/fwxapi/rest/v1/Dataset"
DEFAULT_DATASET_ID = "G5RMG"


@dataclass
class BatchDetail:
    product_name: str
    product_code: str
    batch_no: str
    lot_no: str

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "BatchDetail":
        return cls(
            product_name=str(payload.get("PRODUCT_NAME") or payload.get("product_name") or ""),
            product_code=str(payload.get("PRODUCT_CODE") or payload.get("product_code") or ""),
            batch_no=str(payload.get("BATCH_NO") or payload.get("batch_no") or ""),
            lot_no=str(payload.get("LOT_NO") or payload.get("lot_no") or ""),
        )


@dataclass
class BatchDataRecord:
    timestamp: str
    batch_no: str
    lot_no: str
    time: Optional[Any]
    status: str
    user_name: str
    critical_params: Dict[str, Any]

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "BatchDataRecord":
        standard_keys = {
            "dt",
            "batch_no",
            "lot_no",
            "time",
            "status",
            "user_name",
            "equipmentcode",
            "equipment_code",
            "equipmenttype",
            "equipment_type",
        }
        critical_params = {key: value for key, value in payload.items() if key.lower() not in standard_keys}

        return cls(
            timestamp=str(payload.get("timestamp") or payload.get("TimeStamp") or payload.get("TIMESTAMP") or payload.get("DT") or ""),
            batch_no=str(payload.get("batch_no") or payload.get("Batch_No") or payload.get("BATCH_NO") or ""),
            lot_no=str(payload.get("lot_no") or payload.get("Lot_No") or payload.get("LOT_NO") or ""),
            time=payload.get("time") if payload.get("time") is not None else payload.get("Time"),
            status=str(payload.get("status") or payload.get("Status") or payload.get("STATUS") or ""),
            user_name=str(payload.get("user_name") or payload.get("User_Name") or payload.get("USER_NAME") or ""),
            critical_params=critical_params,
        )


@dataclass
class AlarmRecord:
    time_ms: float
    msg_proc: int
    state_after: int
    msg_class: int
    msg_number: int
    var1: str
    var2: str
    var3: str
    var4: str
    var5: str
    var6: str
    var7: str
    var8: str
    time_string: str
    msg_text: str
    plc: str
    dt: str

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "AlarmRecord":
        return cls(
            time_ms=float(payload.get("Time_ms") or payload.get("time_ms") or payload.get("TIME_MS") or 0),
            msg_proc=int(payload.get("MsgProc") or payload.get("msg_proc") or payload.get("MSG_PROC") or 0),
            state_after=int(payload.get("StateAfter") or payload.get("state_after") or payload.get("STATE_AFTER") or 0),
            msg_class=int(payload.get("MsgClass") or payload.get("msg_class") or payload.get("MSG_CLASS") or 0),
            msg_number=int(payload.get("MsgNumber") or payload.get("msg_number") or payload.get("MSG_NUMBER") or 0),
            var1=str(payload.get("Var1") or payload.get("var1") or payload.get("VAR1") or ""),
            var2=str(payload.get("Var2") or payload.get("var2") or payload.get("VAR2") or ""),
            var3=str(payload.get("Var3") or payload.get("var3") or payload.get("VAR3") or ""),
            var4=str(payload.get("Var4") or payload.get("var4") or payload.get("VAR4") or ""),
            var5=str(payload.get("Var5") or payload.get("var5") or payload.get("VAR5") or ""),
            var6=str(payload.get("Var6") or payload.get("var6") or payload.get("VAR6") or ""),
            var7=str(payload.get("Var7") or payload.get("var7") or payload.get("VAR7") or ""),
            var8=str(payload.get("Var8") or payload.get("var8") or payload.get("VAR8") or ""),
            time_string=str(payload.get("TimeString") or payload.get("time_string") or payload.get("TIME_STRING") or ""),
            msg_text=str(payload.get("MsgText") or payload.get("msg_text") or payload.get("MSG_TEXT") or ""),
            plc=str(payload.get("PLC") or payload.get("plc") or payload.get("Plc") or ""),
            dt=str(payload.get("DT") or payload.get("dt") or payload.get("DateTime") or ""),
        )


@dataclass
class AuditRecord:
    record_id: str
    time_stamp: str
    delta_to_utc: str
    user_id: str
    object_id: str
    description: str
    comment: Optional[str]
    checksum: str
    dt: str

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "AuditRecord":
        return cls(
            record_id=str(payload.get("RecordID") or payload.get("record_id") or payload.get("RECORD_ID") or ""),
            time_stamp=str(payload.get("TimeStamp") or payload.get("time_stamp") or payload.get("TIMESTAMP") or ""),
            delta_to_utc=str(payload.get("DeltaToUTC") or payload.get("delta_to_utc") or payload.get("DELTA_TO_UTC") or ""),
            user_id=str(payload.get("UserID") or payload.get("user_id") or payload.get("USER_ID") or ""),
            object_id=str(payload.get("ObjectID") or payload.get("object_id") or payload.get("OBJECT_ID") or ""),
            description=str(payload.get("Description") or payload.get("description") or payload.get("DESCRIPTION") or ""),
            comment=payload.get("Comment") if payload.get("Comment") is not None else payload.get("comment"),
            checksum=str(payload.get("Checksum") or payload.get("checksum") or payload.get("CHECKSUM") or ""),
            dt=str(payload.get("DT") or payload.get("dt") or payload.get("DATE_TIME") or ""),
        )


def build_point_name(dataset: str, params: Optional[Dict[str, Any]] = None, dataset_id: str = DEFAULT_DATASET_ID) -> str:
    params = params or {}
    inner = ", ".join(f"@{key}='{value}'" for key, value in params.items())
    if inner:
        return f"db:{dataset_id}.{dataset}<{inner}>"
    return f"db:{dataset_id}.{dataset}"


def fetch_dataset(
    dataset_name: str,
    params: Optional[Dict[str, Any]] = None,
    base_url: str = BASE_URL,
    dataset_id: str = DEFAULT_DATASET_ID,
) -> Dict[str, Any]:
    point_name = build_point_name(dataset_name, params, dataset_id=dataset_id)
    encoded = quote(point_name, safe="")
    url = f"{base_url}?pointname={encoded}"
    response = requests.get(url, timeout=30)
    response.raise_for_status()
    payload = response.json()
    if payload.get("status") != "success":
        raise ValueError(f"Source API error for {dataset_name}: {payload}")
    return payload


def fetch_batch_details(
    batch_no: Optional[str] = None,
    lot_no: Optional[str] = None,
    dataset_id: str = DEFAULT_DATASET_ID,
) -> List[BatchDetail]:
    """Fetch raw batch details. Filter by batch_no and lot_no when provided.

    This keeps the raw payload layer while allowing the scheduler to update
    records for a specific batch and lot during ingestion.
    """
    payload = fetch_dataset("BATCHDETAILS", dataset_id=dataset_id)
    data = payload.get("data", [])

    if batch_no or lot_no:
        filtered = []
        for item in data:
            item_batch = str(item.get("BATCH_NO") or item.get("batch_no") or "")
            item_lot = str(item.get("LOT_NO") or item.get("lot_no") or "")
            if batch_no and item_batch != batch_no:
                continue
            if lot_no and item_lot != lot_no:
                continue
            filtered.append(item)
        data = filtered

    return [BatchDetail.from_dict(item) for item in data]


def fetch_batch_data(batch_no: str, lot_no: str, dataset_id: str = DEFAULT_DATASET_ID) -> List[BatchDataRecord]:
    payload = fetch_dataset("BATCHDATA", {"BATCH_NO": batch_no, "LOT_NO": lot_no}, dataset_id=dataset_id)
    data = payload.get("data", [])
    return [BatchDataRecord.from_dict(item) for item in data]


def fetch_alarm_data(from_time: str, to_time: str, dataset_id: str = DEFAULT_DATASET_ID) -> List[AlarmRecord]:
    payload = fetch_dataset("ALARMDATA", {"FROMTIME": from_time, "TOTIME": to_time}, dataset_id=dataset_id)
    data = payload.get("data", [])
    return [AlarmRecord.from_dict(item) for item in data]


def fetch_audit_data(from_time: str, to_time: str, dataset_id: str = DEFAULT_DATASET_ID) -> List[AuditRecord]:
    payload = fetch_dataset("AUDITDATA", {"FROMTIME": from_time, "TOTIME": to_time}, dataset_id=dataset_id)
    data = payload.get("data", [])
    return [AuditRecord.from_dict(item) for item in data]


def _format_batch_timestamp(value: str) -> Optional[str]:
    text = str(value or "").strip()
    if not text:
        return None

    candidates = [
        "%Y-%m-%dT%H:%M:%S.%f",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%d %H:%M:%S",
    ]
    for fmt in candidates:
        try:
            return datetime.strptime(text, fmt).strftime("%Y-%m-%d %H:%M:%S")
        except ValueError:
            continue

    try:
        return datetime.fromisoformat(text).strftime("%Y-%m-%d %H:%M:%S")
    except ValueError:
        return None


def _resolve_product_context(
    batch_details: List[BatchDetail],
    batch_no: Optional[str] = None,
    lot_no: Optional[str] = None,
) -> BatchDetail:
    if batch_no or lot_no:
        for item in batch_details:
            if batch_no and item.batch_no != batch_no:
                continue
            if lot_no and item.lot_no != lot_no:
                continue
            return item

    if not batch_details:
        raise ValueError("No batch details available to resolve a product context")

    return batch_details[0]


def build_product_response(
    *,
    dataset_id: str = DEFAULT_DATASET_ID,
    batch_no: Optional[str] = None,
    lot_no: Optional[str] = None,
    from_time: Optional[str] = None,
    to_time: Optional[str] = None,
) -> Dict[str, Any]:
    """Build one complete product response using the fetched batch context.

    The batch detail drives the batch number and lot number. The batch telemetry
    then drives the alarm and audit time window so the example stays dynamic and
    aligned with the scheduler ingestion flow.
    """
    details = fetch_batch_details(batch_no=batch_no, lot_no=lot_no, dataset_id=dataset_id)
    selected_detail = _resolve_product_context(details, batch_no=batch_no, lot_no=lot_no)

    batch_rows = fetch_batch_data(selected_detail.batch_no, selected_detail.lot_no, dataset_id=dataset_id)
    if batch_rows:
        resolved_from = from_time or _format_batch_timestamp(batch_rows[0].timestamp) or batch_rows[0].timestamp
        resolved_to = to_time or _format_batch_timestamp(batch_rows[-1].timestamp) or batch_rows[-1].timestamp
    else:
        resolved_from = from_time or datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        resolved_to = to_time or datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    alarm_rows = fetch_alarm_data(resolved_from, resolved_to, dataset_id=dataset_id)
    audit_rows = fetch_audit_data(resolved_from, resolved_to, dataset_id=dataset_id)

    return {
        "batch_details": [asdict(item) for item in details],
        "batch_data": [asdict(item) for item in batch_rows],
        "alarm_data": [asdict(item) for item in alarm_rows],
        "audit_data": [asdict(item) for item in audit_rows],
        "resolved_batch": {
            "dataset_id": dataset_id,
            "batch_no": selected_detail.batch_no,
            "lot_no": selected_detail.lot_no,
            "product_code": selected_detail.product_code,
            "product_name": selected_detail.product_name,
        },
        "resolved_window": {
            "from_time": resolved_from,
            "to_time": resolved_to,
        },
    }


if __name__ == "__main__":
    product_response = build_product_response(dataset_id=DEFAULT_DATASET_ID)

    print("Product Response:")
    print(json.dumps(product_response, indent=2, default=str))
