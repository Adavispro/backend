"""
Data Cleaner and Normalization Engine for Compression Data.
Strips XML/HTML tags, trims whitespace, standardizes datetimes, and parses numbers.
"""

from __future__ import annotations

import re
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Union

TAG_REGEX = re.compile(r"<[^>]+>")


def strip_tags(value: Any) -> str:
    """Remove XML/HTML tags such as <c>, <b>, </c>, </b> and strip whitespace."""
    if value is None:
        return ""
    text = str(value)
    cleaned = TAG_REGEX.sub("", text)
    return cleaned.strip()


def clean_key(key: Any) -> str:
    """Clean dictionary key by stripping tags and excess whitespace."""
    if key is None:
        return ""
    return strip_tags(key).replace("  ", " ").strip()


def clean_value(value: Any) -> Any:
    """Recursively clean a value: strip tags/whitespace for strings, or recurse on containers."""
    if value is None:
        return None
    if isinstance(value, str):
        val = strip_tags(value)
        if val in ("-", "null", "NULL", "N/A", "NA", "None", ""):
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


def parse_datetime(value: Any) -> Optional[datetime]:
    """Parse various datetime formats (e.g. DD/MM/YYYY HH:MM:SS, MM/DD/YYYY, ISO)."""
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
        "%m/%d/%Y %H:%M:%S",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%dT%H:%M:%S%z",
        "%Y-%m-%dT%H:%M:%S.%f%z",
        "%Y-%m-%dT%H:%M:%S.%f",
        "%d-%m-%Y %H:%M:%S",
        "%Y/%m/%d %H:%M:%S",
        "%Y%m%d_%H%M%S",
        "%Y-%m-%d-%H-%M-%S",
        "%d/%m/%Y",
        "%m/%d/%Y",
        "%Y-%m-%d",
    ]

    for fmt in date_formats:
        try:
            return datetime.strptime(text, fmt)
        except ValueError:
            continue

    return None


def parse_numeric(value: Any) -> Optional[Union[int, float]]:
    """Parse string/float/int to a clean numeric value (int if whole number, else float)."""
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return int(value) if isinstance(value, float) and value.is_integer() else value

    text = strip_tags(str(value)).replace(",", "").replace("%", "").strip()
    if not text or text in ("-", "null", "NULL", "N/A", "NA", "None"):
        return None

    match = re.search(r"[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?", text)
    if not match:
        return None

    try:
        val = float(match.group(0))
        return int(val) if val.is_integer() else val
    except ValueError:
        return None


def clean_str(value: Any) -> str:
    """Strip tags and return a trimmed string."""
    if value is None:
        return ""
    return strip_tags(value).strip()


def parse_datetime_val(value: Any) -> Optional[str]:
    """Parse a datetime value and return ISO formatted string or date string."""
    dt = parse_datetime(value)
    if dt:
        return dt.strftime("%Y-%m-%d %H:%M:%S")
    return clean_str(value) if value is not None else None


def clean_obj_values(obj: Any) -> Any:
    """Clean all values in a nested dict or list."""
    return clean_value(obj)

