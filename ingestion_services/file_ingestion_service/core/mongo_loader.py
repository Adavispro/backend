"""
MongoDB Ingestion and Synchronization Loader for Compression Machine (MC081).
Ingests batch runs, recipes, alarms, audits, login history, live status, and checkpoints into MongoDB.
Implements Master Data Synchronization and Cascading Truncate & Load.
"""

from __future__ import annotations

import os
import sys
import logging
from pathlib import Path
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
from pymongo import MongoClient, UpdateOne
from .cleaner import clean_str, parse_numeric, parse_datetime

# Resolve common module
ingestion_services_dir = Path(__file__).resolve().parent.parent.parent
if str(ingestion_services_dir) not in sys.path:
    sys.path.insert(0, str(ingestion_services_dir))

try:
    from common.master_data_sync import (
        MasterDataSyncManager,
        normalize_batch_size_str,
        sanitize_code,
        EQUIPMENT_DEFINITIONS,
    )
except ImportError:
    MasterDataSyncManager = None
    normalize_batch_size_str = lambda x: str(x)
    sanitize_code = lambda x: str(x)
    EQUIPMENT_DEFINITIONS = []

logger = logging.getLogger("compression.mongo_loader")


class MongoCompressionLoader:
    """Handles all MongoDB writes for the Sejong Compression Ingestion Service."""

    def __init__(self, mongo_uri: str, db_name: str = "adavis_platform") -> None:
        self.mongo_uri = mongo_uri
        self.db_name = db_name
        self.client = MongoClient(self.mongo_uri)
        self.db = self.client[self.db_name]

        if MasterDataSyncManager is not None:
            self.master_sync = MasterDataSyncManager(self.db)
        else:
            self.master_sync = None

        self._ensure_indexes_and_master()

    def _ensure_indexes_and_master(self) -> None:
        """Creates necessary indexes and ensures MC081 is present in equipment master."""
        try:
            # Batch timeseries collection
            self.db["iiot_ts_batch_MC081"].create_index([("meta.batchNo", 1), ("observedAt", 1)], background=True)
            self.db["iiot_ts_batch_MC081"].create_index([("compression_details.metadata.sourceFile", 1)], unique=True, sparse=True, background=True)

            # Alarm collection
            self.db["iiot_ts_alarm_MC081"].create_index([("alarmId", 1)], unique=True, background=True)
            self.db["iiot_ts_alarm_MC081"].create_index([("timestamp", 1)], background=True)
            self.db["iiot_ts_alarm_MC081"].create_index([("event_time", -1)], background=True)
            self.db["iiot_ts_alarm_MC081"].create_index([("meta.equipmentId", 1), ("event_time", -1)], background=True)

            # Audit collection (Operational parameter changes)
            self.db["iiot_ts_audit_MC081"].create_index([("auditId", 1)], unique=True, background=True)
            self.db["iiot_ts_audit_MC081"].create_index([("timestamp", 1)], background=True)
            self.db["iiot_ts_audit_MC081"].create_index([("event_time", -1)], background=True)
            self.db["iiot_ts_audit_MC081"].create_index([("meta.equipmentId", 1), ("event_time", -1)], background=True)

            # Login history collection
            self.db["iiot_ts_login_MC081"].create_index([("loginId", 1)], unique=True, background=True)
            self.db["iiot_ts_login_MC081"].create_index([("timestamp", 1)], background=True)

            # Checkpoint index
            self.db["iiot_ingestion_checkpoint"].create_index([("equipmentCode", 1)], unique=True, background=True)

            # Batch summary indexes
            self.db["iiot_batch_summary"].create_index([("batchNo", 1)], background=True)

            # Synchronize Equipment Master and Critical Parameters
            if self.master_sync:
                self.master_sync.sync_equipment_master()
                self.master_sync.sync_critical_parameters()
        except Exception as e:
            logger.warning(f"Error ensuring indexes or equipment master: {e}")

    def prepare_database_for_ingestion(
        self,
        ingestion_mode: str = "APPEND",
        backup_enabled: bool = False,
    ) -> Dict[str, Any]:
        """
        Configure database mode: APPEND vs TRUNCATE_AND_LOAD.
        If TRUNCATE_AND_LOAD, executes cascading truncate of MC081 transactional and batch data.
        """
        mode = ingestion_mode.strip().upper()
        report: Dict[str, Any] = {
            "mode": mode,
            "truncated_collections": [],
        }

        if self.db is None:
            return report

        if mode == "TRUNCATE_AND_LOAD":
            if self.master_sync:
                cascade_res = self.master_sync.cascade_truncate_and_reset(target_equipment=["MC081"], reset_master=False)
                report["truncated_collections"].extend(cascade_res.get("truncatedCollections", []))
            else:
                for col_name in ["iiot_ts_batch_MC081", "iiot_ts_alarm_MC081", "iiot_ts_audit_MC081", "iiot_ts_login_MC081"]:
                    self.db[col_name].delete_many({})
                    report["truncated_collections"].append(col_name)

            self._ensure_indexes_and_master()
            logger.info("Compression Ingestion: TRUNCATE_AND_LOAD reset completed.")
        else:
            logger.info("Compression Ingestion: Running in APPEND mode.")
            self._ensure_indexes_and_master()

        return report

    def get_checkpoint(self, equipment_code: str = "MC081") -> Optional[Dict[str, Any]]:
        """Retrieve last ingestion checkpoint for equipment."""
        try:
            return self.db["iiot_ingestion_checkpoint"].find_one({"equipmentCode": equipment_code})
        except Exception as e:
            logger.error(f"Error getting checkpoint for {equipment_code}: {e}")
            return None

    def update_checkpoint(
        self,
        equipment_code: str = "MC081",
        last_ingested_date: Optional[str] = None,
        last_report_file: Optional[str] = None,
        last_sawc_id: Optional[int] = None,
        status: str = "COMPLETED"
    ):
        """Update ingestion checkpoint in MongoDB."""
        try:
            update_fields: Dict[str, Any] = {
                "equipmentCode": equipment_code,
                "equipmentId": equipment_code,
                "equipmentType": "COMP",
                "stageId": "STAGE-4",
                "status": status,
                "lastRunAt": datetime.now(timezone.utc).isoformat(),
                "updatedAt": datetime.now(timezone.utc)
            }
            if last_ingested_date:
                update_fields["lastIngestedDate"] = last_ingested_date
            if last_report_file:
                update_fields["lastProcessedReportFile"] = last_report_file
            if last_sawc_id is not None:
                update_fields["lastProcessedSawcId"] = last_sawc_id

            self.db["iiot_ingestion_checkpoint"].update_one(
                {"equipmentCode": equipment_code},
                {"$set": update_fields, "$setOnInsert": {"createdAt": datetime.now(timezone.utc)}},
                upsert=True
            )
        except Exception as e:
            logger.error(f"Error updating checkpoint: {e}")

    def load_batch_report(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        """
        Ingests standardized batch document into:
        1. iiot_ts_batch_MC081 (Time-series run)
        2. iiot_batch_recipes (Recipe parameters)
        3. iiot_batch_summary (Stage 4 execution record)
        4. iiot_equipment_live_status (Live status)
        5. Master Data (Product Master & Recipe Master validation)
        """
        meta = dict(payload.get("meta", {}))
        metrics = payload.get("metrics", {})
        comp_data = payload.get("compression_details") or payload.get("compression_data") or {}
        observed_at = payload.get("observedAt", "")
        event_time = payload.get("event_time", observed_at)

        batch_no = clean_str(meta.get("batchNo")) or "UNKNOWN"
        lot_no = clean_str(meta.get("derivedLotNo") or meta.get("lotNo")) or batch_no
        product_name = clean_str(meta.get("productName"))
        product_code = clean_str(meta.get("productCode")) or product_name
        operator_name = clean_str(meta.get("operatorName")) or clean_str(meta.get("userId"))
        user_id = clean_str(meta.get("userId")) or ""
        source_file = comp_data.get("metadata", {}).get("sourceFile", "")

        report_dt = parse_datetime(observed_at) or datetime.now(timezone.utc)

        # Ensure complete meta keys for unified querying
        meta["equipmentId"] = "MC081"
        meta["equipmentCode"] = "MC081"
        meta["equipmentType"] = "COMP"
        meta["batchNo"] = batch_no
        meta["lotNo"] = lot_no
        meta["derivedLotNo"] = lot_no
        meta["productName"] = product_name
        meta["productCode"] = product_code
        meta["operatorName"] = operator_name
        meta["userId"] = user_id
        meta["status"] = "COMPLETED"

        # 1. Master Data Synchronization & Validation
        recipe = comp_data.get("recipeSettings", {})
        target_qty = recipe.get("targetQuantity")
        batch_size_str = f"{target_qty:,} Tabs" if isinstance(target_qty, (int, float)) else "Not available"
        recipe_name = f"{product_name} Compression Recipe" if product_name else "Compression Recipe"
        recipe_code = f"RCP-{sanitize_code(product_name or batch_no)}-COMP"

        if self.master_sync:
            self.master_sync.validate_and_sync_batch_master(
                product_code=product_code,
                product_name=product_name,
                recipe_code=recipe_code,
                recipe_name=recipe_name,
                batch_size=batch_size_str,
                equipment_code="MC081",
                equipment_type="COMP",
            )

        # 2. Ingest into iiot_ts_batch_MC081
        ts_batch_doc = {
            "observedAt": observed_at,
            "event_time": event_time,
            "ingestedAt": datetime.now(timezone.utc).isoformat(),
            "meta": meta,
            "metrics": metrics,
            "compression_details": comp_data,
            "createdAt": datetime.now(timezone.utc)
        }

        filter_query = {
            "$or": [
                {"compression_details.metadata.sourceFile": source_file},
                {"compression_data.metadata.sourceFile": source_file},
                {"metadata.sourceFile": source_file}
            ]
        } if source_file else {"meta.batchNo": batch_no, "observedAt": observed_at}

        self.db["iiot_ts_batch_MC081"].update_one(
            filter_query,
            {
                "$set": ts_batch_doc,
                "$unset": {
                    "compression_data": "",
                    "batchInfo": "",
                    "pressureData": "",
                    "operationValues": "",
                    "tabletCounters": "",
                    "recipeSettings": "",
                    "signatures": "",
                    "metadata": "",
                    "batchId": ""
                }
            },
            upsert=True
        )

        # 3. Ingest Recipe into iiot_batch_recipes
        recipe_doc = {
            "equipmentCode": "MC081",
            "equipmentType": "COMP",
            "batchNo": batch_no,
            "lotNo": lot_no,
            "derivedLotNo": lot_no,
            "productName": product_name,
            "recipeCode": recipe_code,
            "recipeName": recipe_name,
            "stageId": "STAGE-4",
            "stageName": "Compression",
            "parameters": recipe,
            "updatedAt": datetime.now(timezone.utc)
        }
        self.db["iiot_batch_recipes"].update_one(
            {"equipmentCode": "MC081", "batchNo": batch_no, "lotNo": lot_no},
            {"$set": recipe_doc, "$setOnInsert": {"createdAt": datetime.now(timezone.utc)}},
            upsert=True
        )

        # 4. Update iiot_batch_summary
        counters = comp_data.get("tabletCounters", {})
        good_count = counters.get("good", {}).get("count")
        total_count = counters.get("totalCounter")
        press = comp_data.get("pressureData", {})
        mean_main = press.get("mainPressure", {}).get("meanKn")
        mean_pre = press.get("prePressure", {}).get("meanKn")
        op_vals = comp_data.get("operationValues", {})
        disk_spd = op_vals.get("diskSpeedRpm")

        stage_comp_data = {
            "stageId": "STAGE-4",
            "stageName": "Compression",
            "equipmentType": "COMP",
            "equipmentCode": "MC081",
            "equipmentId": "MC081",
            "assetId": "MC081",
            "lotNo": lot_no,
            "derivedLotNo": lot_no,
            "sourceFile": source_file,
            "sequenceOrder": 4,
            "executionStatus": "COMPLETED",
            "stageStartAt": report_dt,
            "stageEndAt": report_dt,
            "operatorName": operator_name,
            "userId": user_id,
            "supervisorName": "",
            "producedQuantity": good_count,
            "targetQuantity": target_qty,
            "recordCount": total_count,
            "criticalMetrics": {
                "meanMainPressureKn": mean_main,
                "meanPrePressureKn": mean_pre,
                "diskSpeedRpm": disk_spd,
                "goodTablets": good_count,
                "totalCounter": total_count
            },
            "approval": {
                "status": "APPROVED",
                "approvedBy": operator_name,
                "approvedAt": report_dt,
                "comments": f"Auto-verified from Sejong {source_file}"
            }
        }

        existing_summary = self.db["iiot_batch_summary"].find_one({"batchNo": batch_no})
        if existing_summary:
            stages = existing_summary.get("stages", [])
            stage_idx = next((i for i, s in enumerate(stages)
                              if s.get("equipmentCode") == "MC081" and
                              (s.get("derivedLotNo") or s.get("lotNo")) == lot_no), None)
            if stage_idx is not None:
                stages[stage_idx] = stage_comp_data
            else:
                stages.append(stage_comp_data)

            self.db["iiot_batch_summary"].update_one(
                {"batchNo": batch_no},
                {
                    "$set": {
                        "stages": stages,
                        "updatedAt": datetime.now(timezone.utc),
                        "productName": product_name or existing_summary.get("productName", ""),
                        "batchSize": batch_size_str,
                        "recipeName": recipe_name,
                        "recipeCode": recipe_code,
                        "derivedLots": sorted(set((existing_summary.get("derivedLots") or []) + [lot_no])),
                        "productionReportCount": len(set((existing_summary.get("derivedLots") or []) + [lot_no])),
                    }
                }
            )
        else:
            new_summary = {
                "batchNo": batch_no,
                "lotNo": batch_no,
                "derivedLots": [lot_no],
                "productionReportCount": 1,
                "productCode": product_code,
                "productName": product_name,
                "batchSize": batch_size_str,
                "recipeName": recipe_name,
                "recipeCode": recipe_code,
                "overallStatus": "IN_PROGRESS",
                "batchStartAt": report_dt,
                "batchEndAt": report_dt,
                "stages": [stage_comp_data],
                "createdAt": datetime.now(timezone.utc),
                "updatedAt": datetime.now(timezone.utc)
            }
            self.db["iiot_batch_summary"].insert_one(new_summary)

        # 5. Upsert iiot_equipment_live_status
        live_status_doc = {
            "equipmentCode": "MC081",
            "equipmentId": "MC081",
            "equipmentName": "MC081 SEJONG 49D Compression Machine",
            "equipmentType": "COMP",
            "stageId": "STAGE-4",
            "stageOrder": 4,
            "stageName": "Compression",
            "status": "RUNNING",
            "currentBatch": batch_no,
            "productName": product_name,
            "operatorName": operator_name,
            "userId": user_id,
            "liveData": {
                "diskSpeedRpm": disk_spd,
                "mainPressureKn": mean_main,
                "prePressureKn": mean_pre,
                "goodTablets": good_count,
                "totalCounter": metrics.get("TOTAL_COUNTER", 0),
                "lastReportFile": source_file
            },
            "lastUpdated": datetime.now(timezone.utc)
        }
        self.db["iiot_equipment_live_status"].update_one(
            {"equipmentCode": "MC081"},
            {"$set": live_status_doc, "$setOnInsert": {"createdAt": datetime.now(timezone.utc)}},
            upsert=True
        )

        return {
            "status": "SUCCESS",
            "batchNo": batch_no,
            "lotNo": lot_no,
            "equipmentId": "MC081",
            "timestamp": report_dt.isoformat()
        }

    def load_alarms(self, alarms: List[Dict[str, Any]]) -> int:
        """Ingests alarm events into iiot_ts_alarm_MC081 with metadata alignment and deduplication."""
        if not alarms:
            return 0
        ops = []
        now_dt = datetime.now(timezone.utc)
        for alm in alarms:
            aid = alm.get("alarmId")
            if not aid:
                continue

            ts_raw = alm.get("timestamp") or alm.get("occurred_time") or alm.get("time")
            parsed_dt = parse_datetime(ts_raw) or now_dt
            event_iso = parsed_dt.isoformat() if hasattr(parsed_dt, "isoformat") else str(ts_raw)

            doc = dict(alm)
            doc["alarmId"] = aid
            doc["event_time"] = event_iso
            doc["eventAt"] = event_iso
            doc["timestamp"] = event_iso
            doc["eventCategory"] = "ALARM"
            doc["severity"] = alm.get("severity", "WARNING")
            doc["alarmName"] = alm.get("alarmName") or alm.get("message") or alm.get("alarm_name") or "Compression Alarm"
            doc["description"] = alm.get("description") or alm.get("message") or doc["alarmName"]
            doc["meta"] = {
                "equipmentId": "MC081",
                "equipmentCode": "MC081",
                "equipment_code": "MC081",
                "equipmentType": "COMP",
                "stageName": "Compression",
            }
            doc["source"] = {"equipmentCode": "MC081", "sourceType": "FILE"}

            ops.append(
                UpdateOne(
                    {"alarmId": aid},
                    {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                    upsert=True
                )
            )
        if ops:
            res = self.db["iiot_ts_alarm_MC081"].bulk_write(ops, ordered=False)
            return (res.upserted_count or 0) + (res.modified_count or 0)
        return 0

    def load_audits(self, audits: List[Dict[str, Any]]) -> int:
        """Ingests audit trail / parameter changes into iiot_ts_audit_MC081 with metadata alignment and deduplication."""
        if not audits:
            return 0
        ops = []
        now_dt = datetime.now(timezone.utc)
        for aud in audits:
            aid = aud.get("auditId")
            if not aid:
                continue

            ts_raw = aud.get("timestamp") or aud.get("time") or aud.get("event_time")
            parsed_dt = parse_datetime(ts_raw) or now_dt
            event_iso = parsed_dt.isoformat() if hasattr(parsed_dt, "isoformat") else str(ts_raw)

            doc = dict(aud)
            doc["auditId"] = aid
            doc["event_time"] = event_iso
            doc["eventAt"] = event_iso
            doc["timestamp"] = event_iso
            doc["eventCategory"] = "EVENT"
            doc["description"] = aud.get("description") or aud.get("action") or aud.get("parameterName") or "Parameter Change"
            doc["meta"] = {
                "equipmentId": "MC081",
                "equipmentCode": "MC081",
                "equipment_code": "MC081",
                "equipmentType": "COMP",
                "stageName": "Compression",
            }
            doc["source"] = {"equipmentCode": "MC081", "sourceType": "FILE"}

            ops.append(
                UpdateOne(
                    {"auditId": aid},
                    {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                    upsert=True
                )
            )
        if ops:
            res = self.db["iiot_ts_audit_MC081"].bulk_write(ops, ordered=False)
            return (res.upserted_count or 0) + (res.modified_count or 0)
        return 0

    def load_logins(self, logins: List[Dict[str, Any]]) -> int:
        """Ingests login / logout events into iiot_ts_login_MC081 with deduplication."""
        if not logins:
            return 0
        ops = []
        now_dt = datetime.now(timezone.utc)
        for log_ev in logins:
            lid = log_ev.get("loginId") or log_ev.get("auditId")
            if not lid:
                continue

            ts_raw = log_ev.get("timestamp") or log_ev.get("loginTime") or log_ev.get("time")
            parsed_dt = parse_datetime(ts_raw) or now_dt
            event_iso = parsed_dt.isoformat() if hasattr(parsed_dt, "isoformat") else str(ts_raw)

            doc = dict(log_ev)
            doc["loginId"] = lid
            doc["event_time"] = event_iso
            doc["timestamp"] = event_iso
            doc["meta"] = {
                "equipmentId": "MC081",
                "equipmentCode": "MC081",
                "equipmentType": "COMP",
            }

            ops.append(
                UpdateOne(
                    {"loginId": lid},
                    {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                    upsert=True
                )
            )
        if ops:
            res = self.db["iiot_ts_login_MC081"].bulk_write(ops, ordered=False)
            return (res.upserted_count or 0) + (res.modified_count or 0)
        return 0
