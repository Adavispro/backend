"""Parse only batch identity fields documented by the supplied API reference."""

import re
from datetime import datetime, timezone


UNSAFE_POINT_VALUE = re.compile(r"['<>@,\r\n]")


def point_value(value):
    text = str(value)
    if UNSAFE_POINT_VALUE.search(text):
        raise ValueError("Batch or lot contains an unsupported character for a dataset pointName")
    return text


def build_point(template, asset_id, batch_no="", lot_no=""):
    return template.format(
        asset_id=point_value(asset_id),
        batch_no=point_value(batch_no),
        lot_no=point_value(lot_no),
    )


def parse_batches(payload):
    if not isinstance(payload, list):
        raise ValueError("Batch_Info must return a JSON array")
    batches = []
    for row in payload:
        if not isinstance(row, dict) or not row.get("BatchNo"):
            continue
        batches.append({
            "batch_no": str(row["BatchNo"]),
            "lot_no": "" if row.get("LotNo") is None else str(row["LotNo"]),
            "product_no": row.get("ProductNo"),
            "batch_start_date": row.get("BatchStartDate"),
            "batch_end_date": row.get("BatchEndDate"),
            "status": "LIVE" if not row.get("BatchEndDate") else "COMPLETED",
        })
    return batches


def batch_sort_date(batch):
    value = batch.get("batch_start_date")
    if not value:
        return float("-inf")
    try:
        parsed = datetime.fromisoformat(value)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.timestamp()
    except (TypeError, ValueError):
        return float("-inf")
