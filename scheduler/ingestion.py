from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta
from typing import Any, Iterable
from uuid import uuid4

from data_service_layer.source_api_client import (
    DEFAULT_DATASET_ID,
    fetch_alarm_data as source_fetch_alarm_data,
    fetch_audit_data as source_fetch_audit_data,
    fetch_batch_data as source_fetch_batch_data,
    fetch_batch_details as source_fetch_batch_details,
)

import os

DATA_INGESTION_START_DATE = os.getenv("DATA_INGESTION_START_DATE", "2026-08-15 06:00:00")
SCHEDULER_RUN_INTERVAL_MINUTES = int(os.getenv("SCHEDULER_INTERVAL_MINUTES", "10"))
MAX_PARALLEL_DATASETS = 6
DEFAULT_DATASET_IDS = (
    "G5RMG",
    "G5FBD",
    "G5OGB",
    "G5COAT",
)


try:
    from pymongo import MongoClient
except ImportError:  # pragma: no cover - optional dependency in some local test runs
    MongoClient = None  # type: ignore[assignment]


def normalize_datetime(value: Any) -> datetime:
    """Normalize common source datetime strings into a plain Python datetime."""
    if isinstance(value, datetime):
        return value

    if value is None or str(value).strip() == "":
        raise ValueError("datetime value is required")

    text = str(value).strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"

    candidates = [
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%dT%H:%M:%S.%f",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%dT%H:%M:%S",
        "%d/%m/%Y %H:%M:%S",
        "%d/%m/%Y %H:%M:%S.%f",
    ]

    for fmt in candidates:
        try:
            return datetime.strptime(text, fmt)
        except ValueError:
            continue

    try:
        return datetime.fromisoformat(text)
    except ValueError as exc:  # pragma: no cover - defensive validation
        raise ValueError(f"Unsupported datetime format: {value}") from exc


def build_time_window(
    last_seen: Any | None = None,
    current_time: Any | None = None,
    window_minutes: int = 10,
) -> tuple[str, str]:
    """Build a time window for alarm/audit ingestion.

    If a last-seen timestamp exists, the scheduler replays from that value to the
    current time. Otherwise it takes the last configured window (default 10 minutes).
    """
    current_dt = normalize_datetime(current_time) if current_time is not None else datetime.now()

    if last_seen is not None:
        from_dt = normalize_datetime(last_seen)
    else:
        from_dt = current_dt - timedelta(minutes=window_minutes)

    return from_dt.strftime("%Y-%m-%d %H:%M:%S"), current_dt.strftime("%Y-%m-%d %H:%M:%S")


def build_batch_lookup(batches: Iterable[dict[str, Any]]) -> dict[str, dict[str, str]]:
    """Return a unique lookup keyed by batch_no|lot_no used to avoid duplicate loads."""
    lookup: dict[str, dict[str, str]] = {}
    for item in batches:
        batch_no = str(item.get("batch_no") or item.get("BATCH_NO") or "").strip()
        lot_no = str(item.get("lot_no") or item.get("LOT_NO") or "").strip()
        if not batch_no or not lot_no:
            continue
        lookup[f"{batch_no}|{lot_no}"] = {"batch_no": batch_no, "lot_no": lot_no}
    return lookup


def iter_time_windows(from_time: str | datetime, to_time: str | datetime, step_hours: int = 2) -> list[tuple[str, str]]:
    """Break a time range into fixed 2-hour windows for replay-safe ingestion."""
    start_dt = normalize_datetime(from_time)
    end_dt = normalize_datetime(to_time)
    windows: list[tuple[str, str]] = []
    cursor = start_dt
    while cursor < end_dt:
        next_dt = min(cursor + timedelta(hours=step_hours), end_dt)
        windows.append((cursor.strftime("%Y-%m-%d %H:%M:%S"), next_dt.strftime("%Y-%m-%d %H:%M:%S")))
        cursor = next_dt
    if not windows:
        windows.append((start_dt.strftime("%Y-%m-%d %H:%M:%S"), end_dt.strftime("%Y-%m-%d %H:%M:%S")))
    return windows


def batch_is_completed(batch_record: dict[str, Any]) -> bool:
    status = str(batch_record.get("batch_status") or batch_record.get("BATCH_STATUS") or batch_record.get("status") or "").strip().upper()
    return status == "COMPLETED" or status.endswith("COMPLETED")


def fetch_batch_details(*, batch_no: str | None = None, lot_no: str | None = None, dataset_id: str = DEFAULT_DATASET_ID):
    return source_fetch_batch_details(batch_no=batch_no, lot_no=lot_no, dataset_id=dataset_id)


def fetch_batch_data(batch_no: str, lot_no: str, dataset_id: str = DEFAULT_DATASET_ID):
    return source_fetch_batch_data(batch_no, lot_no, dataset_id=dataset_id)


def fetch_alarm_data(from_time: str, to_time: str, dataset_id: str = DEFAULT_DATASET_ID):
    return source_fetch_alarm_data(from_time, to_time, dataset_id=dataset_id)


def fetch_audit_data(from_time: str, to_time: str, dataset_id: str = DEFAULT_DATASET_ID):
    return source_fetch_audit_data(from_time, to_time, dataset_id=dataset_id)


class SchedulerIngestionService:
    """Ingest raw source data into structured product/batch/lot collections.

    Collection layout:
      - products: product master keyed by product_code
      - batches: batch master keyed by product_code + batch_no + lot_no
      - batch_events: raw batch telemetry events by batch_no + lot_no + timestamp
      - alarm_events: alarm/acknowledgement records keyed by source alarm id or timestamp
      - audit_events: audit trail records keyed by source record id or timestamp
    """

    def __init__(
        self,
        mongo_uri: str | None = None,
        db_name: str = "adavis_platform",
        client: Any | None = None,
    ) -> None:
        self.mongo_uri = mongo_uri or "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin"
        self.db_name = db_name
        self.client = client or (MongoClient(self.mongo_uri) if MongoClient is not None else None)
        self.db = self.client[self.db_name] if self.client is not None else None

    def _dataset_collection_name(self, base_name: str, dataset_id: str) -> str:
        if base_name == "batch_events":
            return f"iiot_ts_batch_{dataset_id}"
        if base_name == "alarm_events":
            return f"iiot_ts_alarm_{dataset_id}"
        if base_name == "audit_events":
            return f"iiot_ts_audit_{dataset_id}"
        return f"{base_name}_{dataset_id}"

    def _dataset_collection(self, base_name: str, dataset_id: str):
        if self.db is None:
            return None
        return self.db[self._dataset_collection_name(base_name, dataset_id)]

    def _state_key(self, base_name: str, dataset_id: str) -> str:
        return f"{dataset_id}:{base_name}"

    def _extract_equipment_code(self, row: dict[str, Any], dataset_id: str) -> str:
        return str(
            row.get("equipment_code")
            or row.get("equipmentCode")
            or row.get("EquipmentCode")
            or row.get("equipment_id")
            or row.get("equipmentId")
            or row.get("EquipmentId")
            or dataset_id
        ).strip()

    def _extract_equipment_type(self, equipment_code: str) -> str:
        code = str(equipment_code or "").strip().upper()
        if code.endswith("RMG") or "RMG" in code:
            return "RMG"
        if code.endswith("FBD") or "FBD" in code:
            return "FBD"
        if code.endswith("OGB") or code.endswith("BLE") or "OGB" in code or "BLE" in code or "OCB" in code:
            return "BLE"
        if code.endswith("COAT") or "COAT" in code:
            return "COAT"
        return "RMG"

    def _equipment_line_key(self, equipment_code: str) -> str:
        code = str(equipment_code or "").strip().upper()
        if len(code) >= 2 and code[0] == "G" and code[1].isdigit():
            return code[:2]
        return code

    def _expected_equipment_codes(self, equipment_code: str) -> list[str]:
        line = self._equipment_line_key(equipment_code)
        if line.startswith("G") and len(line) == 2:
            return [f"{line}RMG", f"{line}FBD", f"{line}OGB", f"{line}COAT"]
        return [equipment_code]

    def _default_stage(self, equipment_code: str, sequence_order: int) -> dict[str, Any]:
        equipment_type = self._extract_equipment_type(equipment_code)
        stage_names = {
            "RMG": "Granulation",
            "FBD": "Drying",
            "BLE": "Blending",
            "COAT": "Coating",
        }
        stage_name = stage_names.get(equipment_type, f"Stage {sequence_order}")
        return {
            "stageId": f"STAGE-{sequence_order}",
            "stageName": stage_name,
            "equipmentType": equipment_type,
            "equipmentCode": equipment_code,
            "equipmentId": equipment_code,
            "sequenceOrder": sequence_order,
            "sequence": sequence_order,
            "executionStatus": "NOT_STARTED",
            "stageStartAt": None,
            "stageEndAt": None,
            "operatorName": "",
            "supervisorName": "",
            "recordCount": 0,
            "approval": {
                "status": "PENDING",
                "approvedBy": "",
                "approvedAt": None,
                "comments": "",
            },
        }

    def _status_to_execution(self, status: str) -> str:
        text = str(status or "").strip().upper()
        if "COMPLETE" in text or text == "STOP":
            return "COMPLETED"
        if not text:
            return "IN_PROGRESS"
        return "IN_PROGRESS"

    def _compute_stage_record_count(self, dataset_id: str, batch_no: str, lot_no: str, equipment_code: str) -> int:
        col = self._dataset_collection("batch_events", dataset_id)
        if col is None:
            return 0
        return int(
            col.count_documents(
                {
                    "meta.batchNo": batch_no,
                    "meta.lotNo": lot_no,
                    "meta.equipmentCode": equipment_code,
                }
            )
        )

    def _recompute_workflow_status(self, stages: list[dict[str, Any]]) -> str:
        execution_states = [str(s.get("executionStatus") or "NOT_STARTED") for s in stages]
        approval_states = [str((s.get("approval") or {}).get("status") or "PENDING") for s in stages]

        if any(state == "REJECTED" for state in approval_states):
            return "REJECTED"

        all_completed = bool(execution_states) and all(state == "COMPLETED" for state in execution_states)
        all_approved = bool(approval_states) and all(state == "APPROVED" for state in approval_states)

        if all_completed and all_approved:
            return "APPROVED"
        elif any(state == "APPROVED" for state in approval_states):
            return "PARTIAL_APPROVED"

        if all_completed:
            return "COMPLETED"

        return "IN_PROGRESS"

    def upsert_batch_summary(
        self,
        *,
        dataset_id: str,
        detail_row: dict[str, Any],
        batch_event_docs: list[dict[str, Any]],
    ) -> None:
        if self.db is None:
            return

        if not batch_event_docs:
            return

        batch_no = str(detail_row.get("batch_no") or detail_row.get("BATCH_NO") or "").strip()
        lot_no = str(detail_row.get("lot_no") or detail_row.get("LOT_NO") or "").strip()
        product_code = str(detail_row.get("product_code") or detail_row.get("PRODUCT_CODE") or "").strip()
        product_name = str(detail_row.get("product_name") or detail_row.get("PRODUCT_NAME") or "").strip()
        if not batch_no or not lot_no or not product_code:
            return

        equipment_code = self._extract_equipment_code(detail_row, dataset_id)
        line_id = self._equipment_line_key(equipment_code)
        expected_codes = self._expected_equipment_codes(equipment_code)
        equipment_types = [self._extract_equipment_type(code) for code in expected_codes]

        observed_times = [doc.get("observedAt") for doc in batch_event_docs if doc.get("observedAt") is not None]
        if not observed_times:
            return

        stage_start = min(observed_times)
        stage_end = max(observed_times)
        latest_doc = max(batch_event_docs, key=lambda d: d.get("observedAt") or datetime.min)
        stage_operator = str((latest_doc.get("meta") or {}).get("operatorName") or "")
        stage_supervisor = str(detail_row.get("supervisor") or detail_row.get("SUPERVISOR") or "").strip()
        stage_status = self._status_to_execution(str((latest_doc.get("meta") or {}).get("status") or ""))
        stage_record_count = self._compute_stage_record_count(dataset_id, batch_no, lot_no, equipment_code)

        col = self.db["iiot_batch_summary"]
        key_filter = {"batchNo": batch_no, "lotNo": lot_no, "productCode": product_code, "lineId": line_id}
        existing = col.find_one(key_filter)

        if existing is None:
            stages: list[dict[str, Any]] = []
            for idx, code in enumerate(expected_codes, start=1):
                stages.append(self._default_stage(code, idx))
        else:
            stages = list(existing.get("stages") or [])
            known_codes = {str(stage.get("equipmentCode") or "") for stage in stages}
            for idx, code in enumerate(expected_codes, start=1):
                if code not in known_codes:
                    stages.append(self._default_stage(code, idx))

        for stage in stages:
            if str(stage.get("equipmentCode") or "") != equipment_code:
                continue
            stage["equipmentType"] = self._extract_equipment_type(equipment_code)
            stage["executionStatus"] = "COMPLETED" if stage_status == "COMPLETED" else stage.get("executionStatus") or "IN_PROGRESS"
            if stage.get("stageStartAt") is None or stage_start < stage.get("stageStartAt"):
                stage["stageStartAt"] = stage_start
            if stage.get("stageEndAt") is None or stage_end > stage.get("stageEndAt"):
                stage["stageEndAt"] = stage_end
            if stage_operator:
                stage["operatorName"] = stage_operator
            if stage_supervisor:
                stage["supervisorName"] = stage_supervisor
            elif stage_operator and not stage.get("supervisorName"):
                stage["supervisorName"] = stage_operator
            stage["recordCount"] = stage_record_count
            break

        stages.sort(key=lambda s: int(s.get("sequenceOrder") or 999))
        overall_status = self._recompute_workflow_status(stages)

        all_starts = [s.get("stageStartAt") for s in stages if s.get("stageStartAt") is not None]
        all_ends = [s.get("stageEndAt") for s in stages if s.get("stageEndAt") is not None]

        now_dt = datetime.utcnow()
        summary_doc = {
            "lineId": line_id,
            "batchNo": batch_no,
            "lotNo": lot_no,
            "productName": product_name,
            "productCode": product_code,
            "overallStatus": overall_status,
            "batchStartAt": min(all_starts) if all_starts else stage_start,
            "batchEndAt": max(all_ends) if all_ends else stage_end,
            "stages": stages,
            "updatedAt": now_dt,
        }

        col.update_one(
            key_filter,
            {
                "$set": summary_doc,
                "$setOnInsert": {"createdAt": now_dt},
            },
            upsert=True,
        )

        try:
            self.db["iiot_equipment_live_status"].update_one(
                {"equipmentId": equipment_code},
                {
                    "$set": {
                        "equipmentId": equipment_code,
                        "currentState": "Running" if "START" in str(stage_status).upper() or stage_status in ("IN_PROGRESS", "COMPLETED") else "Idle",
                        "stateReason": f"Batch in progress: {batch_no}",
                        "lastBatchNo": batch_no,
                        "lastLotNo": lot_no,
                        "lastEventAt": now_dt.isoformat() + "Z",
                        "heartbeatAt": now_dt.isoformat() + "Z",
                        "updatedAt": now_dt,
                    },
                    "$setOnInsert": {"createdAt": now_dt},
                },
                upsert=True,
            )
        except Exception:
            pass

    def _ensure_timeseries_collection(self, collection_name: str, time_field: str = "event_time") -> None:
        if self.db is None:
            return

        existing = set(self.db.list_collection_names())
        if collection_name in existing:
            return

        try:
            self.db.create_collection(
                collection_name,
                timeseries={
                    "timeField": time_field,
                    "metaField": "meta",
                    "granularity": "seconds",
                },
            )
        except Exception:
            # Fallback for environments where time-series options are not available.
            self.db.create_collection(collection_name)

    def _coerce_event_time(self, *candidates: Any) -> datetime:
        for value in candidates:
            if value is None or str(value).strip() == "":
                continue
            try:
                return normalize_datetime(value)
            except ValueError:
                continue
        return datetime.utcnow()

    def _drop_index_if_exists(self, collection_name: str, index_name: str) -> None:
        if self.db is None:
            return
        try:
            self.db[collection_name].drop_index(index_name)
        except Exception:
            pass

    def fetch_raw_source_snapshot(
        self,
        *,
        dataset_id: str = DEFAULT_DATASET_ID,
        last_seen: Any | None = None,
        current_time: Any | None = None,
        batch_no: str | None = None,
        lot_no: str | None = None,
    ) -> dict[str, Any]:
        """Load the raw source snapshot from the mock dataset and return it as dynamic scheduler input."""
        current_dt = normalize_datetime(current_time) if current_time is not None else datetime.now()
        from_time, to_time = build_time_window(last_seen=last_seen, current_time=current_dt)

        batch_details = [
            item.__dict__ if hasattr(item, "__dict__") else item
            for item in fetch_batch_details(batch_no=batch_no, lot_no=lot_no, dataset_id=dataset_id)
        ]

        batch_lookup = build_batch_lookup(batch_details)
        batch_rows: list[dict[str, Any]] = []
        for key, row in batch_lookup.items():
            record_batch_no = row["batch_no"]
            record_lot_no = row["lot_no"]
            batch_rows.extend(
                [
                    item.__dict__ if hasattr(item, "__dict__") else item
                    for item in fetch_batch_data(record_batch_no, record_lot_no, dataset_id=dataset_id)
                ]
            )

        alarm_rows = [
            item.__dict__ if hasattr(item, "__dict__") else item
            for item in fetch_alarm_data(from_time, to_time, dataset_id=dataset_id)
        ]
        audit_rows = [
            item.__dict__ if hasattr(item, "__dict__") else item
            for item in fetch_audit_data(from_time, to_time, dataset_id=dataset_id)
        ]

        return {
            "dataset_id": dataset_id,
            "batch_details": batch_details,
            "batch_rows": batch_rows,
            "alarm_rows": alarm_rows,
            "audit_rows": audit_rows,
            "from_time": from_time,
            "to_time": to_time,
        }

    def get_ingestion_state(self, key: str) -> str | None:
        if self.db is None:
            return None
        state = self.db.ingestion_state.find_one({"_id": key})
        if not state:
            return None
        return str(state.get("last_seen") or "")

    def set_ingestion_state(self, key: str, timestamp: str) -> None:
        if self.db is None:
            return
        self.db.ingestion_state.update_one(
            {"_id": key},
            {"$set": {"last_seen": timestamp, "updated_at": datetime.utcnow()}},
            upsert=True,
        )

    def _start_job_run(self, dataset_id: str, scheduler_time: datetime) -> str:
        if self.db is None:
            return ""

        job_run_id = f"JOB-{dataset_id}-{scheduler_time.strftime('%Y%m%d%H%M%S')}-{uuid4().hex[:8]}"
        self.db.iiot_ingestion_job_run.insert_one(
            {
                "jobRunId": job_run_id,
                "datasetId": dataset_id,
                "status": "RUNNING",
                "startedAt": datetime.utcnow(),
                "schedulerTime": scheduler_time,
                "runIntervalMinutes": SCHEDULER_RUN_INTERVAL_MINUTES,
                "createdAt": datetime.utcnow(),
                "updatedAt": datetime.utcnow(),
            }
        )
        return job_run_id

    def _finish_job_run(
        self,
        *,
        job_run_id: str,
        status: str,
        batch_result: dict[str, Any] | None,
        event_result: dict[str, Any] | None,
        error: str | None = None,
    ) -> None:
        if self.db is None or not job_run_id:
            return

        update_doc: dict[str, Any] = {
            "status": status,
            "completedAt": datetime.utcnow(),
            "updatedAt": datetime.utcnow(),
            "processedBatches": int((batch_result or {}).get("processed_batches") or 0),
            "eventWindowStatus": str((event_result or {}).get("status") or ""),
            "eventWindowFrom": (event_result or {}).get("from_time"),
            "eventWindowTo": (event_result or {}).get("to_time"),
        }
        if error:
            update_doc["error"] = error

        self.db.iiot_ingestion_job_run.update_one(
            {"jobRunId": job_run_id},
            {"$set": update_doc},
            upsert=False,
        )

    def _update_checkpoints(
        self,
        *,
        dataset_id: str,
        batch_result: dict[str, Any],
        event_result: dict[str, Any],
    ) -> None:
        if self.db is None:
            return

        now_dt = datetime.utcnow()
        checkpoint_rows = [
            {
                "datasetId": dataset_id,
                "streamType": "BATCH_DETAILS",
                "status": "SUCCESS",
                "lastProcessedAt": now_dt,
                "processedCount": int(batch_result.get("processed_batches") or 0),
                "cursorFrom": None,
                "cursorTo": None,
            },
            {
                "datasetId": dataset_id,
                "streamType": "BATCH_SUMMARY",
                "status": "SUCCESS",
                "lastProcessedAt": now_dt,
                "processedCount": int(batch_result.get("processed_batches") or 0),
                "cursorFrom": None,
                "cursorTo": None,
            },
            {
                "datasetId": dataset_id,
                "streamType": "ALARM_EVENTS",
                "status": "SUCCESS" if str(event_result.get("status") or "").lower() != "failed" else "FAILED",
                "lastProcessedAt": now_dt,
                "processedCount": None,
                "cursorFrom": event_result.get("from_time"),
                "cursorTo": event_result.get("to_time"),
            },
            {
                "datasetId": dataset_id,
                "streamType": "AUDIT_EVENTS",
                "status": "SUCCESS" if str(event_result.get("status") or "").lower() != "failed" else "FAILED",
                "lastProcessedAt": now_dt,
                "processedCount": None,
                "cursorFrom": event_result.get("from_time"),
                "cursorTo": event_result.get("to_time"),
            },
        ]

        for row in checkpoint_rows:
            self.db.iiot_ingestion_checkpoint.update_one(
                {"datasetId": row["datasetId"], "streamType": row["streamType"]},
                {
                    "$set": {
                        "status": row["status"],
                        "lastProcessedAt": row["lastProcessedAt"],
                        "processedCount": row["processedCount"],
                        "cursorFrom": row["cursorFrom"],
                        "cursorTo": row["cursorTo"],
                        "updatedAt": now_dt,
                    },
                    "$setOnInsert": {
                        "createdAt": now_dt,
                    },
                },
                upsert=True,
            )

    def ensure_indexes(self, dataset_ids: Iterable[str] | None = None) -> None:
        if self.db is None:
            return

        dataset_ids = tuple(dataset_ids or DEFAULT_DATASET_IDS)

        self.db.ingestion_state.create_index([("_id", 1)])
        self.db.iiot_ingested_events_registry.create_index([("createdAt", 1)], expireAfterSeconds=2592000)
        self.db.iiot_ingestion_job_run.create_index([("jobRunId", 1)], unique=True)
        self.db.iiot_ingestion_job_run.create_index([("datasetId", 1), ("startedAt", -1)])
        self.db.iiot_ingestion_job_run.create_index([("status", 1), ("startedAt", -1)])
        self._drop_index_if_exists("iiot_ingestion_checkpoint", "datasetId_1_streamType_1")
        self._drop_index_if_exists("iiot_ingestion_checkpoint", "equipmentId_1_streamType_1")
        self.db.iiot_ingestion_checkpoint.create_index([("datasetId", 1), ("streamType", 1)])
        self.db.iiot_ingestion_checkpoint.create_index([("status", 1), ("updatedAt", -1)])
        self.db.products.create_index([("product_code", 1)], unique=True)
        self.db.products.create_index([("product_name", 1)])
        self._drop_index_if_exists("iiot_batch_summary", "tenantId_1_plantId_1_areaId_1_equipmentId_1_batchNo_1")
        self._drop_index_if_exists("iiot_batch_summary", "batchNo_1_lotNo_1_productCode_1")
        self.db.iiot_batch_summary.create_index([("lineId", 1), ("batchNo", 1), ("lotNo", 1), ("productCode", 1)])
        self.db.iiot_batch_summary.create_index([("overallStatus", 1), ("updatedAt", -1)])
        self.db.iiot_batch_summary.create_index([("lineId", 1), ("productCode", 1), ("overallStatus", 1), ("updatedAt", -1)])
        self.db.iiot_batch_summary.create_index([("stages.equipmentType", 1), ("stages.executionStatus", 1)])
        self.db.iiot_batch_summary.create_index([("stages.approval.status", 1), ("updatedAt", -1)])

        for dataset_id in dataset_ids:
            batch_collection = self._dataset_collection_name("batch_events", dataset_id)
            alarm_collection = self._dataset_collection_name("alarm_events", dataset_id)
            audit_collection = self._dataset_collection_name("audit_events", dataset_id)

            self._ensure_timeseries_collection(batch_collection, time_field="observedAt")
            self._ensure_timeseries_collection(alarm_collection, time_field="event_time")
            self._ensure_timeseries_collection(audit_collection, time_field="event_time")

            self._drop_index_if_exists(batch_collection, "product_code_1_batch_no_1_lot_no_1_timestamp_1")
            self._drop_index_if_exists(batch_collection, "event_time_1_product_code_1_batch_no_1_lot_no_1")
            self._drop_index_if_exists(alarm_collection, "msg_number_1_msg_text_1")
            self._drop_index_if_exists(alarm_collection, "acknowledged_1_acknowledged_by_1")
            self._drop_index_if_exists(alarm_collection, "event_time_1_msg_number_1")
            self._drop_index_if_exists(audit_collection, "event_time_1_record_id_1")

            self.db[batch_collection].create_index(
                [("meta.batchNo", 1), ("meta.lotNo", 1), ("observedAt", -1)]
            )
            self.db[batch_collection].create_index(
                [("meta.equipmentCode", 1), ("meta.equipmentType", 1), ("observedAt", -1)]
            )
            self.db[batch_collection].create_index([("source.datasetId", 1), ("observedAt", -1)])

            self.db[alarm_collection].create_index(
                [("msg_number", 1), ("dt", 1)]
            )
            self.db[alarm_collection].create_index([("meta.equipment_code", 1), ("event_time", -1)])
            self.db[alarm_collection].create_index([("msg_number", 1), ("event_time", -1)])

            self.db[audit_collection].create_index(
                [("record_id", 1)]
            )
            self.db[audit_collection].create_index([("meta.equipment_code", 1), ("event_time", -1)])
            self.db[audit_collection].create_index([("event_time", -1), ("record_id", 1)])

    def sync_batch_events(self, batch_rows: Iterable[dict[str, Any]], dataset_id: str) -> list[dict[str, Any]]:
        if self.db is None:
            return []

        documents: list[dict[str, Any]] = []
        for row in batch_rows:
            batch_no = str(row.get("batch_no") or row.get("Batch_No") or row.get("BATCH_NO") or "").strip()
            lot_no = str(row.get("lot_no") or row.get("Lot_No") or row.get("LOT_NO") or "").strip()
            if not batch_no or not lot_no:
                continue

            critical_params = row.get("critical_params")
            if not isinstance(critical_params, dict):
                critical_params = {}
                for k, v in row.items():
                    if k not in (
                        "DT", "dt", "Time", "time", "Batch_No", "batch_no", "BATCH_NO",
                        "Lot_No", "lot_no", "LOT_NO", "Status", "status", "STATUS",
                        "User_Name", "user_name", "USER_NAME", "EquipmentCode",
                        "EquipmentType", "equipment_type", "equipmentType"
                    ):
                        try:
                            if isinstance(v, (int, float)):
                                critical_params[k] = float(v)
                            elif isinstance(v, str) and v.replace(".", "", 1).isdigit():
                                critical_params[k] = float(v)
                        except (ValueError, TypeError):
                            pass

            status = str(row.get("status") or row.get("Status") or row.get("STATUS") or "").strip()
            operator_name = str(row.get("user_name") or row.get("User_Name") or row.get("USER_NAME") or "").strip()
            equipment_type = str(row.get("equipment_type") or row.get("equipmentType") or row.get("EquipmentType") or "").strip().upper()
            equipment_code = self._extract_equipment_code(row, dataset_id)
            observed_at = self._coerce_event_time(
                row.get("DT") or row.get("dt"),
                row.get("timestamp") or row.get("TimeStamp") or row.get("TIMESTAMP"),
                row.get("time") or row.get("Time"),
            )

            batch_events_collection = self._dataset_collection("batch_events", dataset_id)
            if self.db is not None:
                dedup_key = f"batch:{dataset_id}:{batch_no}:{lot_no}:{equipment_code}:{observed_at.isoformat()}"
                try:
                    self.db.iiot_ingested_events_registry.insert_one({
                        "_id": dedup_key,
                        "datasetId": dataset_id,
                        "type": "batch",
                        "createdAt": datetime.utcnow()
                    })
                except Exception:
                    # Atomic collision on duplicate key: another worker already ingested this point
                    continue
            elif batch_events_collection is not None:
                existing = batch_events_collection.find_one({
                    "meta.batchNo": batch_no,
                    "meta.lotNo": lot_no,
                    "meta.equipmentCode": equipment_code,
                    "observedAt": observed_at,
                })
                if existing is not None:
                    continue

            event_doc = {
                "observedAt": observed_at,
                "event_time": observed_at,
                "meta": {
                    "batchNo": batch_no,
                    "lotNo": lot_no,
                    "operatorName": operator_name,
                    "equipmentType": equipment_type,
                    "equipmentCode": equipment_code,
                    "status": status,
                },
                "source": {
                    "datasetId": dataset_id,
                },
                "metrics": critical_params,
                "ingestedAt": datetime.utcnow(),
            }

            try:
                batch_events_collection.insert_one(event_doc)
            except Exception:
                continue
            documents.append(event_doc)
        return documents

    def sync_alarm_events(self, alarm_rows: Iterable[dict[str, Any]], dataset_id: str) -> list[dict[str, Any]]:
        if self.db is None:
            return []

        documents: list[dict[str, Any]] = []
        for idx, row in enumerate(alarm_rows):
            timestamp = str(row.get("timestamp") or row.get("TimeStamp") or row.get("TIMESTAMP") or row.get("DT") or row.get("Occurred_Time") or row.get("occurred_time") or row.get("TimeString") or "")
            event_time = self._coerce_event_time(timestamp, row.get("DT"), row.get("Occurred_Time"))
            equipment_code = self._extract_equipment_code(row, dataset_id)
            msg_number = row.get("msg_number") if row.get("msg_number") is not None else row.get("MsgNumber") or (idx + 1)
            dt_str = str(row.get("dt") or row.get("DT") or row.get("Occurred_Time") or row.get("occurred_time") or timestamp or "")
            time_str = str(row.get("time_string") or row.get("TimeString") or row.get("Duration") or row.get("duration") or "")
            msg_text = str(row.get("msg_text") or row.get("MsgText") or row.get("Alarm_Name") or row.get("alarm_name") or row.get("AlarmName") or "Alarm").strip()
            state_after = int(row.get("state_after") if row.get("state_after") is not None else row.get("StateAfter") if row.get("StateAfter") is not None else 0)

            alarm_events_collection = self._dataset_collection("alarm_events", dataset_id)

            # Extract or determine occurred_time and resolved_time based on StateAfter
            occurred_time = str(row.get("occurred_time") or row.get("Occurred_Time") or "")
            resolved_time = str(row.get("resolved_time") or row.get("Resolved_Time") or "")
            duration = str(row.get("duration") or row.get("Duration") or "")

            if state_after == 1:
                # StateAfter == 1 signifies alarm RESOLVED based on message text
                if not resolved_time:
                    resolved_time = dt_str or time_str

                # Try to pair with an existing active/unresolved alarm for this message text
                if alarm_events_collection is not None:
                    open_alarm = alarm_events_collection.find_one({
                        "meta.equipment_code": equipment_code,
                        "msg_text": msg_text,
                        "state_after": {"$ne": 1},
                    })
                    if open_alarm is not None:
                        occ_dt = self._coerce_event_time(open_alarm.get("occurred_time") or open_alarm.get("dt"))
                        res_dt = event_time or self._coerce_event_time(resolved_time)
                        if occ_dt and res_dt:
                            diff_sec = int(abs((res_dt - occ_dt).total_seconds()))
                            h = diff_sec // 3600
                            m = (diff_sec % 3600) // 60
                            s = diff_sec % 60
                            duration = f"{h:02d}:{m:02d}:{s:02d}"

                        alarm_events_collection.update_one(
                            {"_id": open_alarm["_id"]},
                            {
                                "$set": {
                                    "resolved_time": resolved_time,
                                    "duration": duration or open_alarm.get("duration") or "-",
                                    "state_after": 1,
                                    "status": "RESOLVED",
                                    "updated_at": datetime.utcnow(),
                                }
                            },
                        )
                        continue
            else:
                # StateAfter == 0 signifies alarm OCCURRED / ACTIVE
                if not occurred_time:
                    occurred_time = dt_str

            if not occurred_time and not resolved_time:
                occurred_time = dt_str

            dedup_key = f"alarm:{dataset_id}:{equipment_code}:{msg_number}:{msg_text}:{dt_str}:{state_after}"
            if self.db is not None:
                try:
                    self.db.iiot_ingested_events_registry.insert_one({
                        "_id": dedup_key,
                        "datasetId": dataset_id,
                        "type": "alarm",
                        "createdAt": datetime.utcnow()
                    })
                except Exception:
                    pass

            event_doc = {
                "time_ms": row.get("time_ms") if row.get("time_ms") is not None else row.get("Time_ms") or 0,
                "msg_proc": row.get("msg_proc") if row.get("msg_proc") is not None else row.get("MsgProc") or 0,
                "state_after": state_after,
                "status": "RESOLVED" if state_after == 1 else "ACTIVE",
                "msg_class": row.get("msg_class") if row.get("msg_class") is not None else row.get("MsgClass") or 0,
                "msg_number": msg_number,
                "alarm_name": msg_text,
                "occurred_time": occurred_time,
                "resolved_time": resolved_time,
                "duration": duration,
                "var1": str(row.get("var1") or row.get("Var1") or ""),
                "var2": str(row.get("var2") or row.get("Var2") or ""),
                "var3": str(row.get("var3") or row.get("Var3") or ""),
                "var4": str(row.get("var4") or row.get("Var4") or ""),
                "var5": str(row.get("var5") or row.get("Var5") or ""),
                "var6": str(row.get("var6") or row.get("Var6") or ""),
                "var7": str(row.get("var7") or row.get("Var7") or ""),
                "var8": str(row.get("var8") or row.get("Var8") or ""),
                "time_string": time_str,
                "msg_text": msg_text,
                "plc": str(row.get("plc") or row.get("PLC") or ""),
                "dt": dt_str,
                "event_time": event_time,
                "meta": {
                    "equipment_code": equipment_code,
                    "msg_number": msg_number,
                    "alarm_name": msg_text,
                },
                "updated_at": datetime.utcnow(),
            }

            try:
                alarm_events_collection.insert_one(event_doc)
            except Exception:
                continue
            documents.append(event_doc)
        return documents

    def sync_audit_events(self, audit_rows: Iterable[dict[str, Any]], dataset_id: str) -> list[dict[str, Any]]:
        if self.db is None:
            return []

        documents: list[dict[str, Any]] = []
        for idx, row in enumerate(audit_rows):
            event_time = self._coerce_event_time(
                row.get("time_stamp") or row.get("TimeStamp") or row.get("TIMESTAMP") or row.get("dateTime") or row.get("DateTime"),
                row.get("dt") or row.get("DT") or row.get("DateTime") or row.get("dateTime"),
            )
            equipment_code = self._extract_equipment_code(row, dataset_id)
            record_id = str(row.get("record_id") or row.get("RecordID") or row.get("RECORD_ID") or (idx + 1))
            dt_str = str(row.get("dt") or row.get("DT") or row.get("DateTime") or row.get("dateTime") or row.get("time_stamp") or row.get("TimeStamp") or "")
            user_name = str(row.get("user_name") or row.get("UserName") or row.get("user_id") or row.get("UserID") or "Operator")
            description = str(row.get("description") or row.get("Description") or row.get("DESCRIPTION") or "Process Event")

            audit_events_collection = self._dataset_collection("audit_events", dataset_id)
            if self.db is not None:
                dedup_key = f"audit:{dataset_id}:{equipment_code}:{record_id}:{dt_str}:{description}"
                try:
                    self.db.iiot_ingested_events_registry.insert_one({
                        "_id": dedup_key,
                        "datasetId": dataset_id,
                        "type": "audit",
                        "createdAt": datetime.utcnow()
                    })
                except Exception:
                    continue
            elif audit_events_collection is not None:
                existing = audit_events_collection.find_one({
                    "meta.equipment_code": equipment_code,
                    "record_id": record_id,
                    "dt": dt_str,
                })
                if existing is not None:
                    continue

            event_doc = {
                "record_id": record_id,
                "time_stamp": str(row.get("time_stamp") or row.get("TimeStamp") or row.get("TIMESTAMP") or dt_str),
                "date_time": dt_str,
                "delta_to_utc": str(row.get("delta_to_utc") or row.get("DeltaToUTC") or row.get("DELTA_TO_UTC") or ""),
                "user_id": str(row.get("user_id") or row.get("UserID") or row.get("USER_ID") or user_name),
                "user_name": user_name,
                "object_id": str(row.get("object_id") or row.get("ObjectID") or row.get("OBJECT_ID") or ""),
                "description": description,
                "old_value": row.get("old_value") if row.get("old_value") is not None else row.get("OldValue"),
                "new_value": row.get("new_value") if row.get("new_value") is not None else row.get("NewValue"),
                "reason": row.get("reason") if row.get("reason") is not None else row.get("Reason"),
                "comment": row.get("comment") if row.get("comment") is not None else row.get("Comment"),
                "checksum": str(row.get("checksum") or row.get("Checksum") or row.get("CHECKSUM") or ""),
                "dt": dt_str,
                "event_time": event_time,
                "meta": {
                    "equipment_code": equipment_code,
                    "record_id": record_id,
                    "description": description,
                    "user_name": user_name,
                },
                "updated_at": datetime.utcnow(),
            }

            try:
                audit_events_collection.insert_one(event_doc)
                if self.db is not None:
                    self.db["iiot_batch_audit_trail"].update_one(
                        {
                            "batchNo": "NL0026008",
                            "lotNo": "01 of 05",
                            "equipmentCode": equipment_code,
                            "recordId": record_id,
                            "description": description,
                        },
                        {
                            "$set": {
                                "auditId": f"audit_{dataset_id}_{record_id}",
                                "batchNo": "NL0026008",
                                "lotNo": "01 of 05",
                                "equipmentCode": equipment_code,
                                "equipmentId": dataset_id,
                                "timestamp": event_time.isoformat() if hasattr(event_time, "isoformat") else str(event_time),
                                "actionCode": description,
                                "action": description,
                                "description": description,
                                "oldValue": row.get("OldValue") or row.get("old_value") or "-",
                                "newValue": row.get("NewValue") or row.get("new_value") or "-",
                                "reason": row.get("Reason") or row.get("reason") or "-",
                                "userId": user_name,
                                "userName": user_name,
                                "userRole": "Supervisor" if "Supervisor" in user_name else "Operator",
                                "createdAt": datetime.utcnow(),
                            }
                        },
                        upsert=True,
                    )
            except Exception:
                continue
            documents.append(event_doc)
        return documents

    def run_ingestion(self, batch_details, batch_data, alarm_data, audit_data, dataset_id: str) -> dict[str, Any]:
        self.ensure_indexes([dataset_id])
        batch_events = self.sync_batch_events(batch_data, dataset_id)
        alarm_events = self.sync_alarm_events(alarm_data, dataset_id)
        audit_events = self.sync_audit_events(audit_data, dataset_id)
        return {
            "products_updated": 0,
            "batch_details": len(batch_details),
            "batch_events": len(batch_events),
            "alarm_events": len(alarm_events),
            "audit_events": len(audit_events),
            "dataset_id": dataset_id,
        }

    def sync_daily_batch_window(self, dataset_id: str, current_time: str | datetime | None = None) -> dict[str, Any]:
        """Fetch the latest batch details for the current day and update each batch/lot record."""
        current_dt = normalize_datetime(current_time) if current_time is not None else datetime.now()
        detail_rows = [
            item.__dict__ if hasattr(item, "__dict__") else item
            for item in fetch_batch_details(dataset_id=dataset_id)
        ]

        batch_results: list[dict[str, Any]] = []
        for row in detail_rows:
            product_code = str(row.get("product_code") or row.get("PRODUCT_CODE") or "").strip()
            batch_no = str(row.get("batch_no") or row.get("BATCH_NO") or "").strip()
            lot_no = str(row.get("lot_no") or row.get("LOT_NO") or "").strip()
            if not product_code or not batch_no or not lot_no:
                continue

            batch_rows = [
                item.__dict__ if hasattr(item, "__dict__") else item
                for item in fetch_batch_data(batch_no, lot_no, dataset_id=dataset_id)
            ]
            if batch_rows:
                batch_event_docs = self.sync_batch_events(batch_rows, dataset_id)
                self.upsert_batch_summary(dataset_id=dataset_id, detail_row=row, batch_event_docs=batch_event_docs)
            batch_results.append({"product_code": product_code, "batch_no": batch_no, "lot_no": lot_no})

        return {"processed_batches": len(batch_results), "batches": batch_results, "dataset_id": dataset_id}

    def sync_event_window(self, dataset_id: str, current_time: str | datetime | None = None, step_hours: int = 2) -> dict[str, Any]:
        """Replays alarm/audit data from the ingestion start date and then from last_seen to current time."""
        current_dt = normalize_datetime(current_time) if current_time is not None else datetime.now()
        start_dt = normalize_datetime(DATA_INGESTION_START_DATE)
        alarm_state_key = self._state_key("alarm_events", dataset_id)
        audit_state_key = self._state_key("audit_events", dataset_id)
        last_seen = self.get_ingestion_state(alarm_state_key) or self.get_ingestion_state(audit_state_key) or DATA_INGESTION_START_DATE

        alarm_events_collection = self._dataset_collection("alarm_events", dataset_id)
        audit_events_collection = self._dataset_collection("audit_events", dataset_id)

        if self.db is not None and alarm_events_collection.count_documents({}) == 0 and audit_events_collection.count_documents({}) == 0:
            windows = iter_time_windows(start_dt, current_dt, step_hours=step_hours)
            for from_time, to_time in windows:
                alarm_rows = [
                    item.__dict__ if hasattr(item, "__dict__") else item
                    for item in fetch_alarm_data(from_time, to_time, dataset_id=dataset_id)
                ]
                audit_rows = [
                    item.__dict__ if hasattr(item, "__dict__") else item
                    for item in fetch_audit_data(from_time, to_time, dataset_id=dataset_id)
                ]
                self.sync_alarm_events(alarm_rows, dataset_id)
                self.sync_audit_events(audit_rows, dataset_id)
                self.set_ingestion_state(alarm_state_key, to_time)
                self.set_ingestion_state(audit_state_key, to_time)
            return {"status": "catchup", "dataset_id": dataset_id, "from_time": start_dt.strftime("%Y-%m-%d %H:%M:%S"), "to_time": current_dt.strftime("%Y-%m-%d %H:%M:%S")}

        from_time = last_seen
        to_time = current_dt.strftime("%Y-%m-%d %H:%M:%S")
        alarm_rows = [
            item.__dict__ if hasattr(item, "__dict__") else item
            for item in fetch_alarm_data(from_time, to_time, dataset_id=dataset_id)
        ]
        audit_rows = [
            item.__dict__ if hasattr(item, "__dict__") else item
            for item in fetch_audit_data(from_time, to_time, dataset_id=dataset_id)
        ]
        self.sync_alarm_events(alarm_rows, dataset_id)
        self.sync_audit_events(audit_rows, dataset_id)
        self.set_ingestion_state(alarm_state_key, to_time)
        self.set_ingestion_state(audit_state_key, to_time)
        return {"status": "incremental", "dataset_id": dataset_id, "from_time": from_time, "to_time": to_time}

    def run_scheduler_cycle(self, current_time: str | datetime | None = None, dataset_ids: Iterable[str] | None = None) -> dict[str, Any]:
        """Execute the scheduled 15-minute ingestion cycle with day-based batch catch-up and incremental event replay."""
        current_dt = normalize_datetime(current_time) if current_time is not None else datetime.now()
        dataset_ids = list(dataset_ids or DEFAULT_DATASET_IDS)
        self.ensure_indexes(dataset_ids)

        batch_results: list[dict[str, Any]] = []
        event_results: list[dict[str, Any]] = []
        dataset_errors: list[dict[str, Any]] = []

        def _run_dataset(dataset_id: str) -> dict[str, Any]:
            job_run_id = self._start_job_run(dataset_id, current_dt)
            try:
                batch_result = self.sync_daily_batch_window(dataset_id, current_dt)
                event_result = self.sync_event_window(dataset_id, current_dt)
                self._update_checkpoints(dataset_id=dataset_id, batch_result=batch_result, event_result=event_result)
                self._finish_job_run(
                    job_run_id=job_run_id,
                    status="SUCCESS",
                    batch_result=batch_result,
                    event_result=event_result,
                )
                return {
                    "dataset_id": dataset_id,
                    "job_run_id": job_run_id,
                    "batch_result": batch_result,
                    "event_result": event_result,
                    "status": "success",
                }
            except Exception as exc:  # pragma: no cover - runtime safety for scheduler
                self._finish_job_run(
                    job_run_id=job_run_id,
                    status="FAILED",
                    batch_result={"processed_batches": 0},
                    event_result={"status": "failed"},
                    error=str(exc),
                )
                return {
                    "dataset_id": dataset_id,
                    "job_run_id": job_run_id,
                    "batch_result": {"dataset_id": dataset_id, "processed_batches": 0, "batches": []},
                    "event_result": {"dataset_id": dataset_id, "status": "failed"},
                    "status": "failed",
                    "error": str(exc),
                }

        max_workers = max(1, min(MAX_PARALLEL_DATASETS, len(dataset_ids)))
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            future_map = {executor.submit(_run_dataset, dataset_id): dataset_id for dataset_id in dataset_ids}
            for future in as_completed(future_map):
                result = future.result()
                batch_results.append(result["batch_result"])
                event_results.append(result["event_result"])
                if result.get("status") == "failed":
                    dataset_errors.append({
                        "dataset_id": result.get("dataset_id"),
                        "error": result.get("error", "unknown error"),
                    })

        order = {dataset_id: idx for idx, dataset_id in enumerate(dataset_ids)}
        batch_results.sort(key=lambda item: order.get(str(item.get("dataset_id")), 10**6))
        event_results.sort(key=lambda item: order.get(str(item.get("dataset_id")), 10**6))

        return {
            "run_interval_minutes": SCHEDULER_RUN_INTERVAL_MINUTES,
            "current_time": current_dt.strftime("%Y-%m-%d %H:%M:%S"),
            "parallel_execution": True,
            "max_parallel_datasets": max_workers,
            "dataset_count": len(dataset_ids),
            "successful_datasets": len(dataset_ids) - len(dataset_errors),
            "failed_datasets": len(dataset_errors),
            "dataset_errors": dataset_errors,
            "batch_results": batch_results,
            "event_results": event_results,
        }
