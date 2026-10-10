"""
Production MongoDB Loader and Time-Series Aggregator.
Implements time-series collections, atomic event deduplication, product master sync,
batch multi-stage summary tracking (RMG -> FBD -> Blender -> Coating), and live equipment status.
"""

from __future__ import annotations

import logging
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional
from uuid import uuid4

# Ensure parent and module directories are in sys.path for robust standalone resolution
base_dir = Path(__file__).resolve().parent.parent
if str(base_dir) not in sys.path:
    sys.path.insert(0, str(base_dir))
if str(base_dir.parent) not in sys.path:
    sys.path.insert(0, str(base_dir.parent))

try:
    # pyrefly: ignore [missing-import]
    from pymongo import MongoClient, UpdateOne  # type: ignore[import-untyped,import-not-found]
except ImportError:
    MongoClient = None
    UpdateOne = None

try:
    from .master_data_sync import (
        TENANT_ID,
        PLANT_ID,
        BLOCK_ID,
        AREA_GRANULATION,
        AREA_BLENDING,
        AREA_COATING,
        MasterDataSyncManager,
        normalize_batch_size_str,
        sanitize_code,
        EQUIPMENT_DEFINITIONS,
    )
    from .backup_manager import export_collections
    from .cleaner import (
        clean_alarm_row,
        clean_audit_row,
        clean_batch_info_row,
        clean_operational_row,
        clean_recipe_data,
        parse_datetime,
        parse_numeric,
        strip_tags,
    )
except ImportError:
    try:
        from core.master_data_sync import (
            TENANT_ID,
            PLANT_ID,
            BLOCK_ID,
            AREA_GRANULATION,
            AREA_BLENDING,
            AREA_COATING,
            MasterDataSyncManager,
            normalize_batch_size_str,
            sanitize_code,
            EQUIPMENT_DEFINITIONS,
        )
        from core.backup_manager import export_collections
        from core.cleaner import (
            clean_alarm_row,
            clean_audit_row,
            clean_batch_info_row,
            clean_operational_row,
            clean_recipe_data,
            parse_datetime,
            parse_numeric,
            strip_tags,
        )
    except ImportError:
        try:
            from api_ingestion_service.core.master_data_sync import (
                TENANT_ID,
                PLANT_ID,
                BLOCK_ID,
                AREA_GRANULATION,
                AREA_BLENDING,
                AREA_COATING,
                MasterDataSyncManager,
                normalize_batch_size_str,
                sanitize_code,
                EQUIPMENT_DEFINITIONS,
            )
            from api_ingestion_service.core.backup_manager import export_collections
            from api_ingestion_service.core.cleaner import (
                clean_alarm_row,
                clean_audit_row,
                clean_batch_info_row,
                clean_operational_row,
                clean_recipe_data,
                parse_datetime,
                parse_numeric,
                strip_tags,
            )
        except ImportError:
            TENANT_ID = "TNT-0001"
            PLANT_ID = "PLNT-0001"
            BLOCK_ID = "PB1"
            AREA_GRANULATION = "AREA-GRAN"
            AREA_BLENDING = "AREA-BLEND"
            AREA_COATING = "AREA-COAT"
            MasterDataSyncManager = None
            normalize_batch_size_str = lambda x: str(x)
            sanitize_code = lambda x: str(x)
            EQUIPMENT_DEFINITIONS = []
            export_collections = lambda **kwargs: {}
            def clean_alarm_row(r, c): return {}
            def clean_audit_row(r, c): return {}
            def clean_batch_info_row(r, c): return {}
            def clean_operational_row(r, c, **kwargs): return {}
            def clean_recipe_data(r): return []
            def parse_datetime(v): return None
            def parse_numeric(v): return None
            def strip_tags(v): return str(v)

logger = logging.getLogger("api_ingestion_service.mongo_loader")


class MongoIngestionLoader:
    def __init__(
        self,
        mongo_uri: Optional[str] = None,
        db_name: str = "adavis_platform",
        client: Optional[Any] = None,
    ) -> None:
        self.mongo_uri = mongo_uri or ""
        self.db_name = db_name
        self.db = None

        if client is not None:
            self.client = client
            self.db = self.client[self.db_name]
        elif MongoClient is not None and self.mongo_uri:
            try:
                self.client = MongoClient(self.mongo_uri, serverSelectionTimeoutMS=2000, connectTimeoutMS=2000)
                # Verify connection
                self.client.admin.command('ping')
                self.db = self.client[self.db_name]
                logger.info(f"Connected to MongoDB database '{self.db_name}'")
            except Exception as e:
                logger.warning(f"MongoDB not reachable at configured URI ({e}). Running in offline/mock database mode.")
                self.client = None
                self.db = None
        else:
            if MongoClient is None and self.mongo_uri:
                logger.error("`pymongo` library is not installed in the active Python environment. Run: pip install pymongo")
            self.client = None
            self.db = None

        if self.db is not None and MasterDataSyncManager is not None:
            self.master_sync = MasterDataSyncManager(self.db)
        else:
            self.master_sync = None

    def _ts_collection_name(self, stream_type: str, asset_code: str) -> str:
        """Stream types: batch, alarm, audit (e.g. iiot_ts_batch_MB003)."""
        return f"iiot_ts_{stream_type}_{asset_code}"

    def prepare_database_for_ingestion(
        self,
        asset_codes: List[str],
        ingestion_mode: str = "APPEND",
        backup_enabled: bool = False,
    ) -> Dict[str, Any]:
        """
        Configure database mode: APPEND vs TRUNCATE_AND_LOAD.
        If TRUNCATE_AND_LOAD and backup_enabled is True, creates a full JSON disk backup before truncate.
        """
        mode = ingestion_mode.strip().upper()
        report: Dict[str, Any] = {
            "mode": mode,
            "backup_created": False,
            "backup_manifest": None,
            "truncated_collections": [],
        }

        if self.db is None:
            return report

        target_collections = [
            "products",
            "iiot_batch_summary",
            "iiot_equipment_live_status",
            "iiot_batch_recipes",
            "iiot_batch_audit_trail",
            "iiot_ingested_events_registry",
        ]
        for code in asset_codes:
            target_collections.extend([
                self._ts_collection_name("batch", code),
                self._ts_collection_name("alarm", code),
                self._ts_collection_name("audit", code),
            ])

        if mode == "TRUNCATE_AND_LOAD":
            # 1. Export collections to disk JSON backup if requested
            if backup_enabled:
                try:
                    manifest = export_collections(
                        db=self.db,
                        collection_names=target_collections,
                        prefix="truncate_backup",
                    )
                    report["backup_created"] = True
                    report["backup_manifest"] = manifest
                    logger.info(f"Disk backup saved before truncate: {manifest.get('backup_directory')}")
                except Exception as e:
                    logger.error(f"Failed to create disk backup before truncate: {e}")

            # 2. Strict cascading truncate and reset
            if self.master_sync:
                cascade_res = self.master_sync.cascade_truncate_and_reset(target_equipment=asset_codes, reset_master=True)
                report["truncated_collections"].extend(cascade_res.get("truncatedCollections", []))
            else:
                existing_cols = set(self.db.list_collection_names())
                for col_name in target_collections:
                    if col_name in existing_cols:
                        col = self.db[col_name]
                        try:
                            col.delete_many({})
                            report["truncated_collections"].append(col_name)
                            logger.info(f"Truncated collection '{col_name}'")
                        except Exception as e:
                            logger.warning(f"Failed to truncate collection '{col_name}': {e}")

            # Re-ensure indexes
            self.ensure_indexes(asset_codes)
        else:
            logger.info("Ingestion running in APPEND mode with atomic deduplication.")
            if self.master_sync:
                self.master_sync.sync_all_master_data()
            self.ensure_indexes(asset_codes)

        return report

    def ensure_indexes(self, asset_codes: List[str]) -> None:
        """Initialize all platform collections and indexes."""
        if self.db is None:
            return

        try:
            # Registry & Checkpoints
            self.db.iiot_ingested_events_registry.create_index([("_id", 1)])
            self.db.iiot_ingested_events_registry.create_index([("createdAt", 1)], expireAfterSeconds=2592000)
            self.db.iiot_ingestion_job_run.create_index([("jobRunId", 1)], unique=True)
            self.db.iiot_ingestion_job_run.create_index([("status", 1), ("startedAt", -1)])
            self.db.iiot_ingestion_checkpoint.create_index([("assetCode", 1), ("streamType", 1)], unique=True)

            # Master Collections
            self.db.products.create_index([("product_code", 1)], unique=True)
            self.db.products.create_index([("product_name", 1)])
            self.db.iiot_equipment_live_status.create_index([("equipmentId", 1)], unique=True)
            self.db.iiot_batch_recipes.create_index([("batchNo", 1), ("lotNo", 1), ("equipmentCode", 1)], unique=True)
            self.db.iiot_batch_audit_trail.create_index([("batchNo", 1), ("lotNo", 1), ("recordId", 1), ("description", 1)])

            # Batch Summary Workflow Indexes
            self.db.iiot_batch_summary.create_index([("batchNo", 1), ("lotNo", 1), ("productCode", 1)], unique=True)
            self.db.iiot_batch_summary.create_index([("overallStatus", 1), ("updatedAt", -1)])
            self.db.iiot_batch_summary.create_index([("stages.equipmentCode", 1), ("stages.executionStatus", 1)])

            # Time-Series Collections for each equipment asset
            for code in asset_codes:
                batch_col = self._ts_collection_name("batch", code)
                alarm_col = self._ts_collection_name("alarm", code)
                audit_col = self._ts_collection_name("audit", code)

                self._ensure_timeseries_collection(batch_col, time_field="observedAt")
                self._ensure_timeseries_collection(alarm_col, time_field="event_time")
                self._ensure_timeseries_collection(audit_col, time_field="event_time")

                self.db[batch_col].create_index([("meta.batchNo", 1), ("meta.lotNo", 1), ("observedAt", -1)])
                self.db[alarm_col].create_index([("meta.equipment_code", 1), ("event_time", -1)])
                self.db[audit_col].create_index([("meta.equipment_code", 1), ("event_time", -1)])
        except Exception as exc:
            logger.error(f"Error ensuring indexes: {exc}")

    def _ensure_timeseries_collection(self, collection_name: str, time_field: str = "observedAt") -> None:
        if self.db is None:
            return
        if collection_name in self.db.list_collection_names():
            return
        try:
            self.db.create_collection(
                collection_name,
                timeseries={"timeField": time_field, "metaField": "meta", "granularity": "seconds"},
            )
        except Exception:
            self.db.create_collection(collection_name)

    def upsert_product(self, product_code: str, product_name: str) -> None:
        if self.db is None or not product_code:
            return
        now_dt = datetime.now(timezone.utc)
        self.db.products.update_one(
            {"product_code": product_code},
            {
                "$set": {"product_name": product_name, "updated_at": now_dt},
                "$setOnInsert": {"product_code": product_code, "created_at": now_dt},
            },
            upsert=True,
        )

    def is_batch_stage_ingested(
        self,
        batch_no: str,
        lot_no: str,
        asset_code: str,
        equipment_type: str = "",
    ) -> bool:
        """Check if batch data for this equipment/stage has already been ingested."""
        if self.db is None or not batch_no:
            return False

        # 1. Check idempotency registry
        dedup_key = f"batch_stage:{asset_code}:{batch_no}:{lot_no or 'NA'}"
        if self.db.iiot_ingested_events_registry.find_one({"_id": dedup_key}):
            return True

        # 2. Check batch summary if stage is already completed with records
        try:
            summary = self.db.iiot_batch_summary.find_one({
                "batchNo": batch_no,
                "lotNo": lot_no,
                "stages": {
                    "$elemMatch": {
                        "$or": [
                            {"equipmentCode": asset_code},
                            {"equipmentId": asset_code},
                            {"equipmentType": equipment_type},
                        ],
                        "executionStatus": "COMPLETED",
                        "recordCount": {"$gt": 0},
                    }
                }
            })
            if summary:
                return True
        except Exception:
            pass

        return False

    def mark_batch_stage_ingested(
        self,
        batch_no: str,
        lot_no: str,
        asset_code: str,
        equipment_type: str = "",
    ) -> None:
        """Mark batch data for this equipment/stage as ingested in the idempotency registry."""
        if self.db is None or not batch_no:
            return

        dedup_key = f"batch_stage:{asset_code}:{batch_no}:{lot_no or 'NA'}"
        try:
            self.db.iiot_ingested_events_registry.update_one(
                {"_id": dedup_key},
                {
                    "$set": {
                        "batchNo": batch_no,
                        "lotNo": lot_no,
                        "assetCode": asset_code,
                        "equipmentType": equipment_type,
                        "type": "batch_stage_completed",
                        "ingestedAt": datetime.now(timezone.utc),
                    }
                },
                upsert=True,
            )
        except Exception as exc:
            logger.debug(f"Could not register batch stage {dedup_key}: {exc}")

    def sync_operational_events(
        self,
        asset_code: str,
        equipment_type: str,
        batch_no: str,
        lot_no: str,
        records: List[Dict[str, Any]],
        default_status: str = "",
        default_operator: str = "",
        users_records: Optional[List[Dict[str, Any]]] = None,
        audit_records: Optional[List[Dict[str, Any]]] = None,
    ) -> List[Dict[str, Any]]:
        if self.db is None or not records:
            return []

        # Resolve fallback operator if not provided
        resolved_operator = default_operator
        if not resolved_operator and users_records:
            for u in users_records:
                uname = str(u.get("User Name") or u.get("user_name") or "")
                if "operator" in uname.lower():
                    resolved_operator = uname
                    break
        if not resolved_operator and audit_records:
            for a in audit_records:
                uname = str(a.get("User Name") or a.get("user_name") or "")
                if "operator" in uname.lower():
                    resolved_operator = uname
                    break
        if not resolved_operator and users_records:
            for u in users_records:
                uname = str(u.get("User Name") or u.get("user_name") or "")
                if uname and "supervisor" not in uname.lower():
                    resolved_operator = uname
                    break

        resolved_status = default_status or "RUNNING"

        col_name = self._ts_collection_name("batch", asset_code)
        col = self.db[col_name]
        inserted_docs: List[Dict[str, Any]] = []

        for row in records:
            cleaned = clean_operational_row(
                row,
                asset_code,
                default_status=resolved_status,
                default_operator=resolved_operator,
            )
            observed_at = cleaned["observed_at"]
            metrics = cleaned["metrics"]
            status = cleaned["status"]
            operator_name = cleaned["operator_name"]

            dedup_key = f"op:{asset_code}:{batch_no}:{lot_no}:{observed_at.isoformat()}"
            try:
                self.db.iiot_ingested_events_registry.insert_one({
                    "_id": dedup_key,
                    "assetCode": asset_code,
                    "type": "operational",
                    "createdAt": datetime.now(timezone.utc),
                })
            except Exception:
                # Deduplication: already ingested
                continue

            doc = {
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "blockId": BLOCK_ID,
                "observedAt": observed_at,
                "event_time": observed_at,
                "meta": {
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "blockId": BLOCK_ID,
                    "batchNo": batch_no,
                    "lotNo": lot_no,
                    "equipmentCode": asset_code,
                    "equipment_code": asset_code,
                    "equipmentId": asset_code,
                    "equipment_id": asset_code,
                    "equipmentType": equipment_type,
                    "equipment_type": equipment_type,
                    "operatorName": operator_name,
                    "status": status,
                },
                "source": {
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "blockId": BLOCK_ID,
                    "assetCode": asset_code,
                },
                "metrics": metrics,
                "ingestedAt": datetime.now(timezone.utc),
            }

            try:
                col.insert_one(doc)
                inserted_docs.append(doc)
            except Exception as exc:
                logger.warning(f"Failed to insert batch event: {exc}")

        return inserted_docs

    def sync_alarms(
        self,
        asset_code: str,
        records: List[Dict[str, Any]],
    ) -> List[Dict[str, Any]]:
        if self.db is None or not records:
            return []

        col_name = self._ts_collection_name("alarm", asset_code)
        col = self.db[col_name]
        inserted_docs: List[Dict[str, Any]] = []

        for row in records:
            cleaned = clean_alarm_row(row, asset_code)
            alarm_name = cleaned["alarm_name"]
            occ_time = cleaned["occurred_time"]
            res_time = cleaned["resolved_time"]
            duration = cleaned["duration"]
            state_after = cleaned["state_after"]
            status = cleaned["status"]

            # Try to pair with active alarm if resolved
            if state_after == 1 and res_time:
                open_alarm = col.find_one({
                    "meta.equipment_code": asset_code,
                    "alarm_name": alarm_name,
                    "state_after": {"$ne": 1},
                })
                if open_alarm:
                    col.update_one(
                        {"_id": open_alarm["_id"]},
                        {
                            "$set": {
                                "resolved_time": res_time,
                                "duration": duration or open_alarm.get("duration") or "-",
                                "state_after": 1,
                                "status": "RESOLVED",
                                "updated_at": datetime.now(timezone.utc),
                            }
                        },
                    )
                    continue

            dedup_key = f"alarm:{asset_code}:{alarm_name}:{occ_time.isoformat()}:{state_after}"
            try:
                self.db.iiot_ingested_events_registry.insert_one({
                    "_id": dedup_key,
                    "assetCode": asset_code,
                    "type": "alarm",
                    "createdAt": datetime.now(timezone.utc),
                })
            except Exception:
                continue

            doc = {
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "blockId": BLOCK_ID,
                "event_time": occ_time,
                "alarm_name": alarm_name,
                "occurred_time": occ_time,
                "resolved_time": res_time,
                "duration": duration,
                "state_after": state_after,
                "status": status,
                "meta": {
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "blockId": BLOCK_ID,
                    "equipment_code": asset_code,
                    "equipmentCode": asset_code,
                    "equipmentId": asset_code,
                    "equipment_id": asset_code,
                    "alarm_name": alarm_name,
                },
                "updated_at": datetime.now(timezone.utc),
            }

            try:
                col.insert_one(doc)
                inserted_docs.append(doc)
            except Exception as exc:
                logger.warning(f"Failed to insert alarm: {exc}")

        return inserted_docs

    def sync_audits(
        self,
        asset_code: str,
        batch_no: str,
        lot_no: str,
        records: List[Dict[str, Any]],
    ) -> List[Dict[str, Any]]:
        if self.db is None or not records:
            return []

        col_name = self._ts_collection_name("audit", asset_code)
        col = self.db[col_name]
        inserted_docs: List[Dict[str, Any]] = []

        for idx, row in enumerate(records):
            cleaned = clean_audit_row(row, asset_code)
            event_time = cleaned["event_time"]
            user_name = cleaned["user_name"]
            user_role = cleaned["user_role"]
            description = cleaned["description"]
            old_val = cleaned["old_value"]
            new_val = cleaned["new_value"]
            reason = cleaned["reason"]
            record_id = str(idx + 1)

            dedup_key = f"audit:{asset_code}:{batch_no}:{lot_no}:{event_time.isoformat()}:{description}"
            try:
                self.db.iiot_ingested_events_registry.insert_one({
                    "_id": dedup_key,
                    "assetCode": asset_code,
                    "type": "audit",
                    "createdAt": datetime.now(timezone.utc),
                })
            except Exception:
                continue

            doc = {
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "blockId": BLOCK_ID,
                "event_time": event_time,
                "record_id": record_id,
                "user_name": user_name,
                "user_role": user_role,
                "description": description,
                "old_value": old_val,
                "new_value": new_val,
                "reason": reason,
                "meta": {
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "blockId": BLOCK_ID,
                    "equipment_code": asset_code,
                    "equipmentCode": asset_code,
                    "equipmentId": asset_code,
                    "equipment_id": asset_code,
                    "batchNo": batch_no,
                    "lotNo": lot_no,
                    "user_name": user_name,
                    "description": description,
                },
                "updated_at": datetime.now(timezone.utc),
            }

            try:
                col.insert_one(doc)
                # Also upsert into iiot_batch_audit_trail
                self.db.iiot_batch_audit_trail.update_one(
                    {
                        "batchNo": batch_no,
                        "lotNo": lot_no,
                        "equipmentCode": asset_code,
                        "description": description,
                        "timestamp": event_time.isoformat(),
                    },
                    {
                        "$set": {
                            "tenantId": TENANT_ID,
                            "plantId": PLANT_ID,
                            "blockId": BLOCK_ID,
                            "auditId": f"audit_{asset_code}_{record_id}_{int(event_time.timestamp())}",
                            "batchNo": batch_no,
                            "lotNo": lot_no,
                            "equipmentCode": asset_code,
                            "equipmentId": asset_code,
                            "equipment_id": asset_code,
                            "timestamp": event_time.isoformat(),
                            "actionCode": description,
                            "action": description,
                            "description": description,
                            "oldValue": old_val,
                            "newValue": new_val,
                            "reason": reason,
                            "userId": user_name,
                            "userName": user_name,
                            "userRole": user_role,
                        },
                    },
                    upsert=True,
                )
                # Check and sync login / logout history
                desc_lower = (description or "").lower()
                if any(k in desc_lower for k in ["login", "log in", "logout", "log out"]):
                    is_login = "login" in desc_lower or "log in" in desc_lower
                    login_doc = {
                        "tenantId": TENANT_ID,
                        "plantId": PLANT_ID,
                        "blockId": BLOCK_ID,
                        "equipmentCode": asset_code,
                        "equipmentId": asset_code,
                        "equipment_id": asset_code,
                        "batchNo": batch_no,
                        "lotNo": lot_no,
                        "userName": user_name,
                        "userRole": user_role,
                        "eventType": "USER_LOGIN" if is_login else "USER_LOGOUT",
                        "description": description,
                        "timestamp": event_time.isoformat(),
                        "createdAt": datetime.now(timezone.utc),
                    }
                    try:
                        self.db.login_history.update_one(
                            {"equipmentCode": asset_code, "userName": user_name, "timestamp": event_time.isoformat()},
                            {"$set": login_doc, "$setOnInsert": {"createdAt": datetime.now(timezone.utc)}},
                            upsert=True,
                        )
                        login_ts_col = self._ts_collection_name("login", asset_code)
                        self.db[login_ts_col].update_one(
                            {"loginId": f"LOG-{asset_code}-{event_time.strftime('%Y%m%d%H%M%S')}-{idx}"},
                            {"$set": login_doc, "$setOnInsert": {"createdAt": datetime.now(timezone.utc)}},
                            upsert=True,
                        )
                    except Exception:
                        pass

                inserted_docs.append(doc)
            except Exception as exc:
                logger.warning(f"Failed to insert audit: {exc}")

        return inserted_docs

    def upsert_recipe(
        self,
        asset_code: str,
        batch_no: str,
        lot_no: str,
        recipe_records: List[Dict[str, Any]],
    ) -> None:
        if self.db is None or not recipe_records:
            return
        sections = clean_recipe_data(recipe_records)
        now_dt = datetime.now(timezone.utc)
        self.db.iiot_batch_recipes.update_one(
            {"batchNo": batch_no, "lotNo": lot_no, "equipmentCode": asset_code},
            {
                "$set": {
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "blockId": BLOCK_ID,
                    "batchNo": batch_no,
                    "lotNo": lot_no,
                    "equipmentCode": asset_code,
                    "equipmentId": asset_code,
                    "equipment_id": asset_code,
                    "sections": sections,
                    "updatedAt": now_dt,
                },
                "$setOnInsert": {"createdAt": now_dt},
            },
            upsert=True,
        )

    def upsert_batch_summary(
        self,
        *,
        batch_no: str,
        lot_no: str,
        product_name: str,
        product_code: str,
        asset_code: str,
        stage_order: int,
        stage_name: str,
        equipment_type: str,
        op_event_docs: List[Dict[str, Any]],
        stage_status: str = "IN_PROGRESS",
        operator_name: str = "",
        supervisor_name: str = "",
        batch_size: Optional[str] = None,
        recipe_name: Optional[str] = None,
        recipe_code: Optional[str] = None,
    ) -> None:
        if self.db is None or not batch_no or not product_code:
            return

        # 1. Master Data Synchronization & Validation
        if self.master_sync:
            self.master_sync.validate_and_sync_batch_master(
                product_code=product_code,
                product_name=product_name,
                recipe_code=recipe_code,
                recipe_name=recipe_name,
                batch_size=batch_size,
                equipment_code=asset_code,
                equipment_type=equipment_type,
            )

        col = self.db.iiot_batch_summary
        key_filter = {"batchNo": batch_no, "lotNo": lot_no, "productCode": product_code}
        existing = col.find_one(key_filter)

        expected_stages = [
            {"stage_id": "STAGE-1", "stage_name": "Granulation", "equipment_type": "RMG", "code": "MB003", "order": 1},
            {"stage_id": "STAGE-2", "stage_name": "Drying", "equipment_type": "FBD", "code": "MB004", "order": 2},
            {"stage_id": "STAGE-3", "stage_name": "Blending", "equipment_type": "BLE", "code": "MB005", "order": 3},
            {"stage_id": "STAGE-4", "stage_name": "Coating", "equipment_type": "COAT", "code": "MB041", "order": 4},
        ]

        if existing is None:
            stages: List[Dict[str, Any]] = []
            for s in expected_stages:
                stages.append({
                    "stageId": s["stage_id"],
                    "stageName": s["stage_name"],
                    "equipmentType": s["equipment_type"],
                    "equipmentCode": s["code"],
                    "equipmentId": s["code"],
                    "assetId": s["code"],
                    "sequenceOrder": s["order"],
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
                })
        else:
            stages = list(existing.get("stages") or [])

        observed_times = [d.get("observedAt") for d in op_event_docs if d.get("observedAt") is not None]
        stage_start = min(observed_times) if observed_times else None
        stage_end = max(observed_times) if observed_times else None
        latest_doc = max(op_event_docs, key=lambda d: d.get("observedAt") or datetime.min) if op_event_docs else {}
        operator_name = operator_name or (latest_doc.get("meta") or {}).get("operatorName") or ""

        # Update matching stage
        for stg in stages:
            if str(stg.get("equipmentCode")) == str(asset_code) or str(stg.get("equipmentId")) == str(asset_code) or str(stg.get("equipmentType")) == str(equipment_type):
                stg["equipmentCode"] = asset_code
                stg["equipmentId"] = asset_code
                stg["assetId"] = asset_code
                stg["executionStatus"] = "COMPLETED" if "COMPLET" in stage_status.upper() or stage_status == "COMPLETED" else "IN_PROGRESS"
                if stage_start and (stg.get("stageStartAt") is None or stage_start < stg.get("stageStartAt")):
                    stg["stageStartAt"] = stage_start
                if stage_end and (stg.get("stageEndAt") is None or stage_end > stg.get("stageEndAt")):
                    stg["stageEndAt"] = stage_end
                if operator_name:
                    stg["operatorName"] = operator_name
                if supervisor_name:
                    stg["supervisorName"] = supervisor_name
                stg["recordCount"] = len(op_event_docs)
                break

        # Recompute overall workflow status
        execution_states = [str(s.get("executionStatus") or "NOT_STARTED") for s in stages]
        approval_states = [str((s.get("approval") or {}).get("status") or "PENDING") for s in stages]

        all_completed = bool(execution_states) and all(state == "COMPLETED" for state in execution_states)
        all_approved = bool(approval_states) and all(state == "APPROVED" for state in approval_states)

        if any(state == "REJECTED" for state in approval_states):
            overall_status = "REJECTED"
        elif all_completed and all_approved:
            overall_status = "APPROVED"
        elif any(state == "APPROVED" for state in approval_states):
            overall_status = "PARTIAL_APPROVED"
        elif all_completed:
            overall_status = "COMPLETED"
        else:
            overall_status = "IN_PROGRESS"

        all_starts = [s.get("stageStartAt") for s in stages if s.get("stageStartAt") is not None]
        all_ends = [s.get("stageEndAt") for s in stages if s.get("stageEndAt") is not None]

        now_dt = datetime.now(timezone.utc)
        norm_size = normalize_batch_size_str(batch_size) if batch_size else ""
        norm_recipe_name = recipe_name or f"{product_name} {stage_name} Recipe"
        norm_recipe_code = recipe_code or f"RCP-{sanitize_code(product_code)}-{equipment_type}"

        summary_doc = {
            "tenantId": TENANT_ID,
            "plantId": PLANT_ID,
            "blockId": BLOCK_ID,
            "batchNo": batch_no,
            "lotNo": lot_no,
            "productName": product_name,
            "productCode": product_code,
            "batchSize": norm_size or existing.get("batchSize", "") if existing else norm_size,
            "recipeName": norm_recipe_name,
            "recipeCode": norm_recipe_code,
            "overallStatus": overall_status,
            "batchStartAt": min(all_starts) if all_starts else stage_start,
            "batchEndAt": max(all_ends) if all_ends else stage_end,
            "stages": stages,
            "updatedAt": now_dt,
        }

        col.update_one(
            key_filter,
            {"$set": summary_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )

        # Update equipment live status
        try:
            self.db.iiot_equipment_live_status.update_one(
                {"equipmentId": asset_code},
                {
                    "$set": {
                        "tenantId": TENANT_ID,
                        "plantId": PLANT_ID,
                        "blockId": BLOCK_ID,
                        "equipmentId": asset_code,
                        "equipment_id": asset_code,
                        "equipmentCode": asset_code,
                        "equipment_code": asset_code,
                        "equipmentType": equipment_type,
                        "equipment_type": equipment_type,
                        "currentState": "Running" if overall_status == "IN_PROGRESS" or "START" in stage_status.upper() else "Idle",
                        "stateReason": f"Batch in progress: {batch_no} ({lot_no})",
                        "lastBatchNo": batch_no,
                        "lastLotNo": lot_no,
                        "lastEventAt": (stage_end or now_dt).isoformat() + "Z",
                        "heartbeatAt": now_dt.isoformat() + "Z",
                        "updatedAt": now_dt,
                    },
                    "$setOnInsert": {"createdAt": now_dt},
                },
                upsert=True,
            )
        except Exception:
            pass

    def start_job_run(self, asset_code: str) -> str:
        if self.db is None:
            return ""
        now_dt = datetime.now(timezone.utc)
        job_run_id = f"JOB-{asset_code}-{now_dt.strftime('%Y%m%d%H%M%S')}-{uuid4().hex[:6]}"
        self.db.iiot_ingestion_job_run.insert_one({
            "jobRunId": job_run_id,
            "assetCode": asset_code,
            "status": "RUNNING",
            "startedAt": now_dt,
            "createdAt": now_dt,
            "updatedAt": now_dt,
        })
        return job_run_id

    def finish_job_run(
        self,
        job_run_id: str,
        status: str,
        processed_batches: int,
        error: Optional[str] = None,
    ) -> None:
        if self.db is None or not job_run_id:
            return
        now_dt = datetime.now(timezone.utc)
        update_doc: Dict[str, Any] = {
            "status": status,
            "completedAt": now_dt,
            "updatedAt": now_dt,
            "processedBatches": processed_batches,
        }
        if error:
            update_doc["error"] = error
        self.db.iiot_ingestion_job_run.update_one(
            {"jobRunId": job_run_id},
            {"$set": update_doc},
        )
