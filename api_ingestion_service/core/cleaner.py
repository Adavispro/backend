"""
Data Cleaning & Normalization Engine for Plant API Responses.
Handles whitespace trimming, XML/HTML tag stripping (<c>, <b>),
multi-format datetime parsing, numeric coercion, and schema normalization.
"""

from __future__ import annotations

import re
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple, Union

TAG_REGEX = re.compile(r"<[^>]+>")


def strip_tags(value: Any) -> str:
    """Remove XML/HTML tags such as <c>, <b>, </c>, </b> and strip whitespace."""
    if value is None:
        return ""
    text = str(value)
    cleaned = TAG_REGEX.sub("", text)
    return cleaned.strip()


def clean_key(key: Any) -> str:
    """Clean dictionary key by stripping tags and whitespace."""
    if key is None:
        return ""
    return strip_tags(key)


def clean_value(value: Any) -> Any:
    """Recursively clean a value: strip tags/whitespace for strings, or recurse on containers."""
    if value is None:
        return None
    if isinstance(value, str):
        val = strip_tags(value)
        if val == "-" or val == "null" or val == "NULL":
            return None
        return val
    if isinstance(value, dict):
        return {clean_key(k): clean_value(v) for k, v in value.items() if clean_key(k)}
    if isinstance(value, list):
        return [clean_value(item) for item in value]
    return value


def clean_dict(data: Dict[str, Any]) -> Dict[str, Any]:
    """Clean all keys and values in a dictionary."""
    if not isinstance(data, dict):
        return {}
    return {clean_key(k): clean_value(v) for k, v in data.items() if clean_key(k)}


def clean_record_list(records: Any) -> List[Dict[str, Any]]:
    """Ensure input is a list of cleaned dictionaries."""
    if not records:
        return []
    if isinstance(records, dict):
        records = [records]
    if not isinstance(records, list):
        return []
    return [clean_dict(item) for item in records if isinstance(item, dict)]


def parse_datetime(value: Any) -> Optional[datetime]:
    """Parse various source datetime formats into a standard datetime object."""
    if value is None:
        return None
    if isinstance(value, datetime):
        return value

    text = strip_tags(str(value))
    if not text or text in ("-", "null", "NULL", "NA", "N/A"):
        return None

    if text.endswith("Z"):
        text = text[:-1] + "+00:00"

    date_formats = [
        "%d/%m/%Y %H:%M:%S",
        "%d/%m/%Y %H:%M:%S.%f",
        "%d/%m/%Y %H:%M",
        "%d-%m-%Y %H:%M:%S",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%dT%H:%M:%S.%f",
        "%Y-%m-%dT%H:%M:%S%z",
        "%Y-%m-%dT%H:%M:%S.%f%z",
    ]

    for fmt in date_formats:
        try:
            parsed = datetime.strptime(text, fmt)
            if parsed.tzinfo is not None:
                parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
            return parsed
        except ValueError:
            continue

    try:
        parsed = datetime.fromisoformat(text)
        if parsed.tzinfo is not None:
            parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
        return parsed
    except ValueError:
        pass

    return None


def parse_numeric(value: Any) -> Optional[Union[int, float]]:
    """Parse string or numeric value into float or int, handling units and missing markers."""
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return value

    text = strip_tags(str(value)).strip()
    if not text or text in ("-", "null", "NULL", "NA", "N/A"):
        return None

    # Handle numbers with units like "230.000 Kg", "600 Sec", "19.5 Amp"
    match = re.search(r"[-+]?\d*\.?\d+", text)
    if match:
        num_str = match.group(0)
        try:
            if "." in num_str:
                return float(num_str)
            return int(num_str)
        except ValueError:
            return None
    return None


def clean_batch_info_row(row: Dict[str, Any]) -> Dict[str, Any]:
    """Clean and normalize a row from Batch_Info."""
    cleaned = clean_dict(row)
    batch_no = str(cleaned.get("BatchNo") or cleaned.get("Batch_No") or cleaned.get("Batch Number") or "").strip()
    lot_no = str(cleaned.get("LotNo") or cleaned.get("Lot_No") or cleaned.get("Lot Number") or "NA").strip()
    product_no = str(cleaned.get("ProductNo") or cleaned.get("Product_No") or cleaned.get("Product Name") or "").strip()
    
    start_dt = parse_datetime(cleaned.get("BatchStartDate") or cleaned.get("Start Time") or cleaned.get("batch_start_date"))
    end_dt = parse_datetime(cleaned.get("BatchEndDate") or cleaned.get("End Time") or cleaned.get("batch_end_date"))

    is_completed = end_dt is not None
    status = "COMPLETED" if is_completed else "IN_PROGRESS"

    return {
        "batch_no": batch_no,
        "lot_no": lot_no if lot_no and lot_no != "-" else "NA",
        "product_name": product_no if product_no and product_no != "NA" else "Standard Product",
        "product_code": product_no if product_no and product_no != "NA" else "PROD-GEN",
        "start_time": start_dt,
        "end_time": end_dt,
        "status": status,
        "raw": row,
    }


def clean_operational_row(
    row: Dict[str, Any],
    equipment_code: str,
    default_status: str = "",
    default_operator: str = "",
) -> Dict[str, Any]:
    """Clean operational time-series data row (RMG, FBD, Coating, Blender)."""
    cleaned = clean_dict(row)

    time_val = (
        cleaned.get("TIME")
        or cleaned.get("Time")
        or cleaned.get("Date Time")
        or cleaned.get("DateTime")
        or cleaned.get("DT")
        or cleaned.get("dt")
    )
    observed_at = parse_datetime(time_val) or datetime.now(timezone.utc)

    status_val = (
        cleaned.get("STATUS")
        or cleaned.get("BLENDER STATUS")
        or cleaned.get("RMG STATUS")
        or cleaned.get("COAT STATUS")
        or cleaned.get("FBD STATUS")
        or cleaned.get("Status")
        or cleaned.get("status")
        or ""
    )
    status = str(status_val).strip() if status_val else default_status

    operator_name = str(
        cleaned.get("User Name")
        or cleaned.get("UserName")
        or cleaned.get("user_name")
        or cleaned.get("OPERATOR")
        or cleaned.get("Operator")
        or ""
    ).strip() or default_operator

    excluded_keys = {
        "time", "dt", "datetime", "date time", "date_time",
        "status", "blender status", "rmg status", "coat status", "fbd status",
        "user name", "username", "user_name", "operator",
        "equipmentcode", "equipment_code", "equipmenttype", "equipment_type",
        "batchno", "batch_no", "batch number", "lotno", "lot_no", "lot number"
    }

    metrics: Dict[str, float] = {}
    for key, val in cleaned.items():
        norm_key = key.lower()
        if norm_key in excluded_keys:
            continue
        num = parse_numeric(val)
        if num is not None:
            clean_metric_key = re.sub(r"[^\w\s\(\)\/°\-\%]", "", key).strip()
            metrics[clean_metric_key] = float(num)

    return {
        "observed_at": observed_at,
        "status": status,
        "operator_name": operator_name,
        "metrics": metrics,
        "equipment_code": equipment_code,
        "raw": cleaned,
    }


def clean_alarm_row(row: Dict[str, Any], equipment_code: str) -> Dict[str, Any]:
    """Clean alarm event row across equipment datasets."""
    cleaned = clean_dict(row)

    alarm_name = str(
        cleaned.get("Alarm Name")
        or cleaned.get("Alarm_Name")
        or cleaned.get("AlarmName")
        or cleaned.get("msg_text")
        or cleaned.get("MsgText")
        or "Alarm"
    ).strip()

    occ_val = (
        cleaned.get("Occured Time")
        or cleaned.get("Occurred Time")
        or cleaned.get("Occurred_Time")
        or cleaned.get("occurred_time")
        or cleaned.get("DT")
        or cleaned.get("dt")
    )
    occurred_time = parse_datetime(occ_val)

    res_val = (
        cleaned.get("Resolved Time")
        or cleaned.get("Resolved_Time")
        or cleaned.get("resolved_time")
    )
    resolved_time = parse_datetime(res_val)

    duration_str = str(cleaned.get("Duration") or cleaned.get("duration") or cleaned.get("TimeString") or "").strip()

    if not duration_str and occurred_time and resolved_time:
        diff_sec = int(abs((resolved_time - occurred_time).total_seconds()))
        h = diff_sec // 3600
        m = (diff_sec % 3600) // 60
        s = diff_sec % 60
        duration_str = f"{h:02d}:{m:02d}:{s:02d}"

    state_after = cleaned.get("state_after") or cleaned.get("StateAfter")
    if state_after is not None:
        state_after = int(parse_numeric(state_after) or 0)
    else:
        state_after = 1 if resolved_time is not None else 0

    status = "RESOLVED" if state_after == 1 or resolved_time is not None else "ACTIVE"

    return {
        "alarm_name": alarm_name,
        "occurred_time": occurred_time or datetime.now(timezone.utc),
        "resolved_time": resolved_time,
        "duration": duration_str or "-",
        "state_after": state_after,
        "status": status,
        "equipment_code": equipment_code,
        "raw": cleaned,
    }


def clean_audit_row(row: Dict[str, Any], equipment_code: str) -> Dict[str, Any]:
    """Clean audit trail row across equipment datasets."""
    cleaned = clean_dict(row)

    dt_val = (
        cleaned.get("Date Time")
        or cleaned.get("Date And Time")
        or cleaned.get("DateTime")
        or cleaned.get("dateTime")
        or cleaned.get("time_stamp")
        or cleaned.get("TimeStamp")
        or cleaned.get("DT")
        or cleaned.get("dt")
    )
    event_time = parse_datetime(dt_val) or datetime.now(timezone.utc)

    user_name = str(
        cleaned.get("User Name")
        or cleaned.get("UserName")
        or cleaned.get("user_id")
        or cleaned.get("UserID")
        or "Operator"
    ).strip()

    description = str(
        cleaned.get("Description")
        or cleaned.get("description")
        or cleaned.get("Action")
        or cleaned.get("action")
        or "Process Event"
    ).strip()

    old_value = cleaned.get("Old Value") or cleaned.get("OldValue") or cleaned.get("old_value")
    new_value = cleaned.get("New Value") or cleaned.get("NewValue") or cleaned.get("new_value")
    reason = cleaned.get("Reason") or cleaned.get("reason")

    user_role = "Supervisor" if "supervisor" in user_name.lower() else "Operator"

    return {
        "event_time": event_time,
        "user_name": user_name,
        "user_role": user_role,
        "description": description,
        "old_value": str(old_value) if old_value is not None else "-",
        "new_value": str(new_value) if new_value is not None else "-",
        "reason": str(reason) if reason is not None else "-",
        "equipment_code": equipment_code,
        "raw": cleaned,
    }


def clean_recipe_data(records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Clean recipe datasets, preserving section groupings and parameter setpoints."""
    cleaned_rows = clean_record_list(records)
    sections: List[Dict[str, Any]] = []
    current_section = "GENERAL"
    parameters: List[Dict[str, Any]] = []

    for row in cleaned_rows:
        raw_param = str(row.get("Parameter") or row.get("PARAMETER") or "").strip()
        raw_val = row.get("Value") or row.get("value") or row.get("VALUE") or ""

        # Check if this row is a section header (e.g. <b>DRY CYCLE 1)
        if raw_param.startswith("<b>") or (raw_val == "" and raw_param.isupper()):
            section_title = strip_tags(raw_param)
            if parameters:
                sections.append({"section": current_section, "parameters": list(parameters)})
                parameters.clear()
            current_section = section_title or "GENERAL"
            continue

        param_name = strip_tags(raw_param)
        val_str = strip_tags(str(raw_val))
        num_val = parse_numeric(val_str)

        parameters.append({
            "parameter": param_name,
            "value_string": val_str,
            "value_numeric": num_val,
        })

    if parameters:
        sections.append({"section": current_section, "parameters": list(parameters)})

    return sections
