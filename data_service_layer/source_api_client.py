#!/usr/bin/env python3
"""Client utilities for retrieving source batch, alarm, and audit data.

Supports the 5 target equipments:
- MB003 (RMG, Asset ID: 10094)
- MB004 (FBD, Asset ID: 10110)
- MB005 (Blender, Asset ID: 10095)
- MB041 (Coater, Asset ID: 10141)
- MB040 (Compression, Asset ID: 10040)

Connects to mock service or live fwxapi REST API endpoint:
GET /fwxapi/rest/v1/Dataset?pointName=db:HMI.<dataset_name><@AssetId='...', ...>
"""

from __future__ import annotations

import json
import logging
import os
import time
from dataclasses import dataclass, asdict
from datetime import datetime
from typing import Any, Dict, List, Optional
from urllib.parse import quote

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

logger = logging.getLogger(__name__)

_env_base = os.getenv("IIOT_API_BASE_URL") or os.getenv("SOURCE_API_BASE_URL", "http://localhost:8000")
_env_endpoint = os.getenv("IIOT_API_ENDPOINT", "/fwxapi/rest/v1/Dataset")
if _env_base.endswith("/Dataset") or _env_base.endswith("/Dataset/"):
    BASE_URL = _env_base.rstrip("/")
elif _env_base.endswith("/"):
    BASE_URL = f"{_env_base}{_env_endpoint.lstrip('/')}"
else:
    BASE_URL = f"{_env_base}/{_env_endpoint.lstrip('/')}"
DEFAULT_DATASET_ID = "MB003"
DEFAULT_TIMEOUT_SECONDS = int(os.getenv("SOURCE_API_TIMEOUT", "30"))
MAX_RETRIES = int(os.getenv("SOURCE_API_MAX_RETRIES", "3"))
BACKOFF_FACTOR = float(os.getenv("SOURCE_API_BACKOFF_FACTOR", "0.5"))

EQUIPMENT_ASSET_MAP = {
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

EQUIPMENT_TYPE_MAP = {
    "MB003": "RMG",
    "MB004": "FBD",
    "MB005": "BLE",
    "MB040": "COMP",
    "MB041": "COAT",
    "G5RMG": "RMG",
    "G5FBD": "FBD",
    "G5OGB": "BLE",
    "G5BLE": "BLE",
    "G5COAT": "COAT",
}

# Global thread-safe session with connection pooling
_SESSION: Optional[requests.Session] = None


def get_source_api_session() -> requests.Session:
    global _SESSION
    if _SESSION is None:
        session = requests.Session()
        retry_strategy = Retry(
            total=MAX_RETRIES,
            backoff_factor=BACKOFF_FACTOR,
            status_forcelist=[500, 502, 503, 504],
            allowed_methods=["GET"],
            raise_on_status=False,
        )
        adapter = HTTPAdapter(
            pool_connections=20,
            pool_maxsize=50,
            max_retries=retry_strategy,
        )
        session.mount("http://", adapter)
        session.mount("https://", adapter)
        _SESSION = session
    return _SESSION


@dataclass
class BatchDetail:
    product_name: str
    product_code: str
    batch_no: str
    lot_no: str

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "BatchDetail":
        p_name = str(
            payload.get("PRODUCT_NAME")
            or payload.get("product_name")
            or payload.get("ProductName")
            or payload.get("Product Name")
            or payload.get("productName")
            or payload.get("ProductNo")
            or ""
        )
        p_code = str(
            payload.get("PRODUCT_CODE")
            or payload.get("product_code")
            or payload.get("ProductCode")
            or payload.get("Product Code")
            or payload.get("productCode")
            or payload.get("ProductNo")
            or ""
        )
        b_no = str(
            payload.get("BATCH_NO")
            or payload.get("batch_no")
            or payload.get("Batch Number")
            or payload.get("BatchNo")
            or payload.get("batchNo")
            or ""
        )
        l_no = str(
            payload.get("LOT_NO")
            or payload.get("lot_no")
            or payload.get("Lot Number")
            or payload.get("LotNo")
            or payload.get("lotNo")
            or ""
        )
        return cls(
            product_name=p_name,
            product_code=p_code,
            batch_no=b_no,
            lot_no=l_no,
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
            "time",
            "timestamp",
            "observedat",
            "batch_no",
            "batchno",
            "lot_no",
            "lotno",
            "status",
            "user_name",
            "username",
            "equipmentcode",
            "equipment_code",
            "equipmenttype",
            "equipment_type",
        }
        critical_params = {}
        for key, value in payload.items():
            norm_key = key.lower().replace(" ", "").replace("_", "")
            if norm_key not in standard_keys:
                try:
                    if isinstance(value, (int, float)):
                        critical_params[key] = float(value)
                    elif isinstance(value, str) and value.replace(".", "", 1).replace("-", "", 1).isdigit():
                        critical_params[key] = float(value)
                    else:
                        critical_params[key] = value
                except (ValueError, TypeError):
                    critical_params[key] = value

        ts = str(
            payload.get("timestamp")
            or payload.get("TimeStamp")
            or payload.get("TIMESTAMP")
            or payload.get("DT")
            or payload.get("dt")
            or payload.get("TIME")
            or payload.get("observedAt")
            or ""
        )
        b_no = str(
            payload.get("batch_no")
            or payload.get("Batch_No")
            or payload.get("BATCH_NO")
            or payload.get("BatchNo")
            or ""
        )
        l_no = str(
            payload.get("lot_no")
            or payload.get("Lot_No")
            or payload.get("LOT_NO")
            or payload.get("LotNo")
            or ""
        )
        status = str(payload.get("status") or payload.get("Status") or payload.get("STATUS") or "")
        user = str(
            payload.get("user_name")
            or payload.get("User_Name")
            or payload.get("USER_NAME")
            or payload.get("User Name")
            or ""
        )

        return cls(
            timestamp=ts,
            batch_no=b_no,
            lot_no=l_no,
            time=payload.get("time") if payload.get("time") is not None else payload.get("Time"),
            status=status,
            user_name=user,
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
    occurred_time: str = ""
    resolved_time: str = ""
    duration: str = ""
    alarm_name: str = ""

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "AlarmRecord":
        occ = str(
            payload.get("Occurred_Time")
            or payload.get("occurred_time")
            or payload.get("DT")
            or payload.get("dt")
            or payload.get("DateTime")
            or ""
        )
        res = str(payload.get("Resolved_Time") or payload.get("resolved_time") or "")
        dur = str(payload.get("Duration") or payload.get("duration") or payload.get("TimeString") or payload.get("time_string") or "")
        name = str(payload.get("Alarm_Name") or payload.get("alarm_name") or payload.get("MsgText") or payload.get("msg_text") or "Alarm")
        return cls(
            time_ms=float(payload.get("Time_ms") or payload.get("time_ms") or payload.get("TIME_MS") or 0),
            msg_proc=int(payload.get("MsgProc") or payload.get("msg_proc") or payload.get("MSG_PROC") or 0),
            state_after=int(payload.get("StateAfter") if payload.get("StateAfter") is not None else payload.get("state_after") if payload.get("state_after") is not None else 0),
            msg_class=int(payload.get("MsgClass") or payload.get("msg_class") or payload.get("MSG_CLASS") or 0),
            msg_number=int(payload.get("MsgNumber") or payload.get("msg_number") or payload.get("MSG_NUMBER") or 1),
            var1=str(payload.get("Var1") or payload.get("var1") or payload.get("VAR1") or ""),
            var2=str(payload.get("Var2") or payload.get("var2") or payload.get("VAR2") or ""),
            var3=str(payload.get("Var3") or payload.get("var3") or payload.get("VAR3") or ""),
            var4=str(payload.get("Var4") or payload.get("var4") or payload.get("VAR4") or ""),
            var5=str(payload.get("Var5") or payload.get("var5") or payload.get("VAR5") or ""),
            var6=str(payload.get("Var6") or payload.get("var6") or payload.get("VAR6") or ""),
            var7=str(payload.get("Var7") or payload.get("var7") or payload.get("VAR7") or ""),
            var8=str(payload.get("Var8") or payload.get("var8") or payload.get("VAR8") or ""),
            time_string=str(payload.get("TimeString") or payload.get("time_string") or payload.get("TIME_STRING") or dur),
            msg_text=name,
            plc=str(payload.get("PLC") or payload.get("plc") or payload.get("Plc") or ""),
            dt=occ,
            occurred_time=occ,
            resolved_time=res,
            duration=dur,
            alarm_name=name,
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
        rec_id = str(payload.get("RecordID") or payload.get("record_id") or payload.get("RECORD_ID") or "AUD-01")
        dt_val = str(payload.get("Date Time") or payload.get("DateTime") or payload.get("DT") or payload.get("dt") or payload.get("TimeStamp") or payload.get("time_stamp") or "")
        user = str(payload.get("User Name") or payload.get("user_name") or payload.get("UserID") or payload.get("user_id") or "")
        desc = str(payload.get("Description") or payload.get("description") or payload.get("action") or "Process Step")
        old_val = payload.get("Old Value") or payload.get("old_value")
        new_val = payload.get("New Value") or payload.get("new_value")
        reason = payload.get("Reason") or payload.get("reason")
        return cls(
            record_id=rec_id,
            time_stamp=dt_val,
            delta_to_utc=str(payload.get("DeltaToUTC") or payload.get("delta_to_utc") or ""),
            user_id=user,
            object_id=str(payload.get("ObjectID") or payload.get("object_id") or ""),
            description=desc,
            comment=str(reason) if reason else None,
            checksum=str(payload.get("Checksum") or payload.get("checksum") or ""),
            dt=dt_val,
        )


def build_point_name(dataset: str, params: Optional[Dict[str, Any]] = None, dataset_id: str = DEFAULT_DATASET_ID) -> str:
    params = dict(params or {})
    asset_id = EQUIPMENT_ASSET_MAP.get(dataset_id, "10094")
    if "AssetId" not in params:
        params["AssetId"] = asset_id

    inner = ", ".join(f"@{key}='{value}'" for key, value in params.items())

    # Map dataset names to HMI production format if requested
    eq_type = EQUIPMENT_TYPE_MAP.get(dataset_id, "RMG")
    if dataset == "BATCHDETAILS" or dataset == "Batch_Info":
        target = "db:HMI.Batch_Info"
    elif dataset == "BATCHDATA":
        target = f"db:HMI.{eq_type}_op_data"
    elif dataset == "PARAMETERSETTINGS":
        target = f"db:HMI.{eq_type}_recipe"
    elif dataset == "ALARMDATA" or dataset == "Alarms":
        target = "db:HMI.Alarms"
    elif dataset == "AUDITDATA" or dataset == "Audit_Trail":
        target = "db:HMI.Audit_Trail"
    elif dataset.startswith("db:"):
        target = dataset
    else:
        target = f"db:HMI.{dataset}"

    if inner:
        return f"{target}<{inner}>"
    return target


def fetch_dataset(
    dataset_name: str,
    params: Optional[Dict[str, Any]] = None,
    base_url: Optional[str] = None,
    dataset_id: str = DEFAULT_DATASET_ID,
    timeout: int = DEFAULT_TIMEOUT_SECONDS,
) -> Dict[str, Any]:
    resolved_base_url = base_url or BASE_URL
    point_name = build_point_name(dataset_name, params, dataset_id=dataset_id)
    encoded = quote(point_name, safe="")
    url = f"{resolved_base_url}?pointName={encoded}"

    session = get_source_api_session()
    try:
        response = session.get(url, timeout=timeout)
    except requests.RequestException as exc:
        # Fallback with pointname lowercase query param
        try:
            url_alt = f"{resolved_base_url}?pointname={encoded}"
            response = session.get(url_alt, timeout=timeout)
        except requests.RequestException:
            logger.error("Source API request failed for dataset=%s point_name=%s error=%s", dataset_name, point_name, str(exc))
            raise RuntimeError(f"Source API connection failed for dataset '{dataset_name}': {str(exc)}") from exc

    if response.status_code >= 400 and response.status_code < 500:
        raise ValueError(f"Source API rejected request for dataset '{dataset_name}' with status {response.status_code}")

    response.raise_for_status()

    try:
        payload = response.json()
    except Exception as exc:
        raise ValueError(f"Invalid JSON payload received from source API for dataset '{dataset_name}'") from exc

    if payload.get("status") != "success":
        error_msg = payload.get("message") or payload.get("error") or "Unknown source error"
        raise ValueError(f"Source API returned error status for '{dataset_name}': {error_msg}")

    return payload


def fetch_batch_details(
    batch_no: Optional[str] = None,
    lot_no: Optional[str] = None,
    dataset_id: str = DEFAULT_DATASET_ID,
) -> List[BatchDetail]:
    """Fetch raw batch details."""
    payload = fetch_dataset("Batch_Info", dataset_id=dataset_id)
    data = payload.get("data", [])

    if batch_no or lot_no:
        filtered = []
        for item in data:
            item_batch = str(item.get("BATCH_NO") or item.get("batch_no") or item.get("BatchNo") or item.get("Batch Number") or "")
            item_lot = str(item.get("LOT_NO") or item.get("lot_no") or item.get("LotNo") or item.get("Lot Number") or "")
            if batch_no and item_batch != batch_no:
                continue
            if lot_no and item_lot != lot_no:
                continue
            filtered.append(item)
        data = filtered

    return [BatchDetail.from_dict(item) for item in data]


def fetch_batch_data(batch_no: str, lot_no: str, dataset_id: str = DEFAULT_DATASET_ID) -> List[BatchDataRecord]:
    payload = fetch_dataset("BATCHDATA", {"BatchNo": batch_no, "LotNo": lot_no}, dataset_id=dataset_id)
    data = payload.get("data", [])
    return [BatchDataRecord.from_dict(item) for item in data]


def fetch_alarm_data(from_time: str, to_time: str, dataset_id: str = DEFAULT_DATASET_ID) -> List[AlarmRecord]:
    payload = fetch_dataset("Alarms", {"fromtime": from_time, "totime": to_time}, dataset_id=dataset_id)
    data = payload.get("data", [])
    return [AlarmRecord.from_dict(item) for item in data]


def fetch_parameter_settings(batch_no: str, lot_no: str, dataset_id: str = DEFAULT_DATASET_ID) -> Any:
    payload = fetch_dataset("PARAMETERSETTINGS", {"BatchNo": batch_no, "LotNo": lot_no}, dataset_id=dataset_id)
    return payload.get("data", {})


def fetch_audit_data(from_time: str, to_time: str, dataset_id: str = DEFAULT_DATASET_ID) -> List[AuditRecord]:
    payload = fetch_dataset("Audit_Trail", {"fromtime": from_time, "totime": to_time}, dataset_id=dataset_id)
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
        "%d/%m/%Y %H:%M:%S",
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
