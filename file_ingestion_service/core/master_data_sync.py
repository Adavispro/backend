"""
Master Data Synchronization Manager for File Ingestion Service (Compression Machine MC081).
Synchronizes equipment master, critical limits, product master, and topology into MongoDB.
"""

from __future__ import annotations

import logging
import re
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

logger = logging.getLogger("compression.master_sync")

TENANT_ID = "TNT-0001"
PLANT_ID = "PLNT-0001"
BLOCK_ID = "PB1"
AREA_COMPRESSION = "AREA-COMP"
ROOM_COMPRESSION = "RM-COMP-01"

EQUIPMENT_DEFINITIONS = [
    {
        "equipmentSeqId": 10081,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_COMPRESSION,
        "roomId": ROOM_COMPRESSION,
        "equipmentId": "MC081",
        "equipment_id": "MC081",
        "equipmentCode": "MC081",
        "equipment_code": "MC081",
        "assetId": "MC081",
        "asset_id": "MC081",
        "equipmentName": "Sejong 49D Compression Machine (MC081)",
        "equipmentType": "CMP",
        "equipment_type": "CMP",
        "equipmentTypeName": "Tablet Compression Machine",
        "stageOrder": 3,
        "stageName": "Compression",
        "make": "SEJONG",
        "model": "MRC-49D",
        "plcType": "OMRON Sysmac CJ1G",
        "dataSource": "MS ACCESS + Excel",
        "isActive": True,
    },
]

CRITICAL_PARAMETERS = {
    "MC081": [
        {"paramId": "PRM-CMP-01", "code": "MAIN_FORCE", "name": "Main Compression Force", "unit": "kN", "base": 18.0, "low": 10.0, "high": 35.0},
        {"paramId": "PRM-CMP-02", "code": "PRE_FORCE", "name": "Pre-Compression Force", "unit": "kN", "base": 4.0, "low": 1.5, "high": 8.0},
        {"paramId": "PRM-CMP-03", "code": "TURRET_SPD", "name": "Turret Speed", "unit": "RPM", "base": 45.0, "low": 20.0, "high": 75.0},
        {"paramId": "PRM-CMP-04", "code": "FEEDER_SPD", "name": "Feeder Speed", "unit": "RPM", "base": 30.0, "low": 10.0, "high": 50.0},
        {"paramId": "PRM-CMP-05", "code": "EJECT_FORCE", "name": "Ejection Force", "unit": "N", "base": 250.0, "low": 50.0, "high": 600.0},
    ],
}


PRODUCT_DEFINITIONS = [
    {"productId": "PRD-AMIS-100", "productCode": "Amisulpride 100mg", "productName": "Amisulpride Tablets 100mg"},
    {"productId": "PRD-AMIS-200", "productCode": "Amisulpride 200mg", "productName": "Amisulpride Tablets 200mg"},
    {"productId": "PRD-MIRT-15", "productCode": "Mirtazapine OD 15mg", "productName": "Mirtazapine OD Tablets 15mg"},
    {"productId": "PRD-MIRT-30", "productCode": "Mirtazapine OD 30mg", "productName": "Mirtazapine OD Tablets 30mg"},
    {"productId": "PRD-MIRT-45", "productCode": "Mirtazapine OD 45mg", "productName": "Mirtazapine OD Tablets 45mg"},
    {"productId": "PRD-SERT-50", "productCode": "Sertraline 50mg", "productName": "Sertraline Tablets 50mg"},
    {"productId": "PRD-SERT-100", "productCode": "Sertraline 100mg", "productName": "Sertraline Tablets 100mg"},
]

RECIPE_DEFINITIONS = [
    {
        "recipeId": "RCP-Sertraline-100mg-COMP",
        "recipeCode": "RCP-Sertraline-100mg-COMP",
        "recipeName": "Sertraline 100mg Compression Recipe",
        "productCode": "Sertraline 100mg",
        "productName": "Sertraline Tablets 100mg",
        "description": "Sejong MRC-49D compression recipe for Sertraline 100mg",
        "version": "1.0",
        "associatedBatchSizes": ["975,000 Tabs", "1,000,000 Tabs"],
    },
    {
        "recipeId": "RCP-Amisulpride-200mg-COMP",
        "recipeCode": "RCP-Amisulpride-200mg-COMP",
        "recipeName": "Amisulpride 200mg Compression Recipe",
        "productCode": "Amisulpride 200mg",
        "productName": "Amisulpride Tablets 200mg",
        "description": "Sejong MRC-49D compression recipe for Amisulpride 200mg",
        "version": "1.0",
        "associatedBatchSizes": ["500,000 Tabs", "1,000,000 Tabs"],
    },
    {
        "recipeId": "RCP-Mirtazapine-OD-45mg-COMP",
        "recipeCode": "RCP-Mirtazapine-OD-45mg-COMP",
        "recipeName": "Mirtazapine OD 45mg Compression Recipe",
        "productCode": "Mirtazapine OD 45mg",
        "productName": "Mirtazapine OD Tablets 45mg",
        "description": "Sejong MRC-49D compression recipe for Mirtazapine OD 45mg",
        "version": "1.0",
        "associatedBatchSizes": ["500,000 Tabs", "1,000,000 Tabs"],
    },
    {
        "recipeId": "RCP-Paroxetine-20mg-COMP",
        "recipeCode": "RCP-Paroxetine-20mg-COMP",
        "recipeName": "Paroxetine 20mg Compression Recipe",
        "productCode": "Paroxetine USP 20mg",
        "productName": "Paroxetine Tablets USP 20mg",
        "description": "Sejong MRC-49D compression recipe for Paroxetine 20mg",
        "version": "1.0",
        "associatedBatchSizes": ["1,000,000 Tabs", "2,000,000 Tabs"],
    },
    {
        "recipeId": "RCP-Lamotrigine-100mg-COMP",
        "recipeCode": "RCP-Lamotrigine-100mg-COMP",
        "recipeName": "Lamotrigine 100mg Compression Recipe",
        "productCode": "LAMOTRIGINE",
        "productName": "Lamotrigine Tablets USP",
        "description": "Sejong MRC-49D compression recipe for Lamotrigine 100mg",
        "version": "1.0",
        "associatedBatchSizes": ["500,000 Tabs", "1,000,000 Tabs"],
    },
    {
        "recipeId": "RCP-Carvedilol-12.5mg-COMP",
        "recipeCode": "RCP-Carvedilol-12.5mg-COMP",
        "recipeName": "Carvedilol 12.5mg Compression Recipe",
        "productCode": "Carvedilol 12.5mg",
        "productName": "Carvedilol Tablets 12.5mg",
        "description": "Sejong MRC-49D compression recipe for Carvedilol 12.5mg",
        "version": "1.0",
        "associatedBatchSizes": ["500,000 Tabs", "1,000,000 Tabs"],
    },
    {
        "recipeId": "RCP-Allopurinol-100mg-COMP",
        "recipeCode": "RCP-Allopurinol-100mg-COMP",
        "recipeName": "Allopurinol 100mg Compression Recipe",
        "productCode": "STAPU1000",
        "productName": "Allopurinol tablets",
        "description": "Sejong MRC-49D compression recipe for Allopurinol 100mg",
        "version": "1.0",
        "associatedBatchSizes": ["1,000,000 Tabs", "2,500,000 Tabs"],
    },
]

RECIPE_MANAGEMENT_DEFINITIONS = [
    # Sertraline 100mg
    {
        "recipeManagementId": "RCM-MC081-SERT-01",
        "productCode": "Sertraline 100mg",
        "recipeCode": "RCP-Sertraline-100mg-COMP",
        "equipmentCode": "MC081",
        "parameterCode": "MAIN_FORCE",
        "parameterName": "Main Compression Force",
        "unitOfMeasure": "kN",
        "targetSetpoint": 18.0,
        "lowLimit": 10.0,
        "highLimit": 35.0,
        "batchSize": "975,000 Tabs",
    },
    {
        "recipeManagementId": "RCM-MC081-SERT-02",
        "productCode": "Sertraline 100mg",
        "recipeCode": "RCP-Sertraline-100mg-COMP",
        "equipmentCode": "MC081",
        "parameterCode": "PRE_FORCE",
        "parameterName": "Pre-Compression Force",
        "unitOfMeasure": "kN",
        "targetSetpoint": 4.0,
        "lowLimit": 1.5,
        "highLimit": 8.0,
        "batchSize": "975,000 Tabs",
    },
    {
        "recipeManagementId": "RCM-MC081-SERT-03",
        "productCode": "Sertraline 100mg",
        "recipeCode": "RCP-Sertraline-100mg-COMP",
        "equipmentCode": "MC081",
        "parameterCode": "TURRET_SPD",
        "parameterName": "Turret Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 45.0,
        "lowLimit": 20.0,
        "highLimit": 75.0,
        "batchSize": "975,000 Tabs",
    },
    {
        "recipeManagementId": "RCM-MC081-SERT-04",
        "productCode": "Sertraline 100mg",
        "recipeCode": "RCP-Sertraline-100mg-COMP",
        "equipmentCode": "MC081",
        "parameterCode": "FEEDER_SPD",
        "parameterName": "Feeder Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 30.0,
        "lowLimit": 10.0,
        "highLimit": 50.0,
        "batchSize": "975,000 Tabs",
    },
    {
        "recipeManagementId": "RCM-MC081-SERT-05",
        "productCode": "Sertraline 100mg",
        "recipeCode": "RCP-Sertraline-100mg-COMP",
        "equipmentCode": "MC081",
        "parameterCode": "EJECT_FORCE",
        "parameterName": "Ejection Force",
        "unitOfMeasure": "N",
        "targetSetpoint": 250.0,
        "lowLimit": 50.0,
        "highLimit": 600.0,
        "batchSize": "975,000 Tabs",
    },
    # Paroxetine 20mg
    {
        "recipeManagementId": "RCM-MC081-PAROX-01",
        "productCode": "Paroxetine USP 20mg",
        "recipeCode": "RCP-Paroxetine-20mg-COMP",
        "equipmentCode": "MC081",
        "parameterCode": "MAIN_FORCE",
        "parameterName": "Main Compression Force",
        "unitOfMeasure": "kN",
        "targetSetpoint": 20.0,
        "lowLimit": 12.0,
        "highLimit": 38.0,
        "batchSize": "1,000,000 Tabs",
    },
    {
        "recipeManagementId": "RCM-MC081-PAROX-02",
        "productCode": "Paroxetine USP 20mg",
        "recipeCode": "RCP-Paroxetine-20mg-COMP",
        "equipmentCode": "MC081",
        "parameterCode": "PRE_FORCE",
        "parameterName": "Pre-Compression Force",
        "unitOfMeasure": "kN",
        "targetSetpoint": 4.5,
        "lowLimit": 2.0,
        "highLimit": 8.5,
        "batchSize": "1,000,000 Tabs",
    },
    {
        "recipeManagementId": "RCM-MC081-PAROX-03",
        "productCode": "Paroxetine USP 20mg",
        "recipeCode": "RCP-Paroxetine-20mg-COMP",
        "equipmentCode": "MC081",
        "parameterCode": "TURRET_SPD",
        "parameterName": "Turret Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 48.0,
        "lowLimit": 25.0,
        "highLimit": 70.0,
        "batchSize": "1,000,000 Tabs",
    },
]


def sanitize_code(value: Any) -> str:
    """Sanitize code strings (alphanumeric with hyphens/underscores)."""
    if value is None:
        return ""
    text = str(value).strip()
    text = re.sub(r"[^A-Za-z0-9_-]", "-", text)
    return re.sub(r"-+", "-", text).strip("-")


def normalize_batch_size_str(value: Any) -> str:
    """Normalize batch size representation to standard string."""
    if value is None:
        return ""
    text = str(value).strip()
    if not text or text.lower() in ("-", "null", "none", "na"):
        return ""
    return text


class MasterDataSyncManager:
    def __init__(self, db: Any) -> None:
        self.db = db

    def sync_equipment_master(self) -> None:
        """Upsert standard equipment records into iiot_equipment_master & iiot_equiment_master."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        for eq in EQUIPMENT_DEFINITIONS:
            doc = dict(eq)
            doc["updatedAt"] = now_dt
            self.db.iiot_equipment_master.update_one(
                {"equipmentId": eq["equipmentId"]},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
            self.db.iiot_equiment_master.update_one(
                {"equipmentId": eq["equipmentId"]},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
        logger.info(f"Synchronized {len(EQUIPMENT_DEFINITIONS)} compression equipment master records.")

    def sync_critical_parameters(self) -> None:
        """Upsert critical process parameters and operational limits."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        for eq_code, params in CRITICAL_PARAMETERS.items():
            for p in params:
                param_doc = {
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "equipmentId": eq_code,
                    "equipmentCode": eq_code,
                    "parameterId": p["paramId"],
                    "parameterCode": p["code"],
                    "parameterName": p["name"],
                    "unitOfMeasure": p["unit"],
                    "dataType": "NUMERIC",
                    "isActive": True,
                    "createdAt": now_dt,
                    "updatedAt": now_dt,
                }
                self.db.iiot_equipment_critical_parameters.update_one(
                    {"equipmentId": eq_code, "parameterId": p["paramId"]},
                    {"$set": param_doc},
                    upsert=True,
                )
                limit_doc = {
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "equipmentId": eq_code,
                    "parameterId": p["paramId"],
                    "parameterCode": p["code"],
                    "baseValue": p["base"],
                    "lowerLimitWarning": p["low"],
                    "lowerLimitCritical": p["low"] * 0.9,
                    "upperLimitWarning": p["high"],
                    "upperLimitCritical": p["high"] * 1.1,
                    "effectiveFrom": datetime(2026, 1, 1, tzinfo=timezone.utc),
                    "isActive": True,
                    "createdAt": now_dt,
                    "updatedAt": now_dt,
                }
                self.db.iiot_equipment_critical_parameters_limit.update_one(
                    {"equipmentId": eq_code, "parameterId": p["paramId"]},
                    {"$set": limit_doc},
                    upsert=True,
                )
        logger.info("Synchronized compression critical parameters and limits.")

    def sync_product_master(self) -> None:
        """Upsert standard compression products into iiot_product_master & products."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        for prod in PRODUCT_DEFINITIONS:
            prod_id = prod.get("productId") or prod["productCode"]
            doc = {
                "productId": prod_id,
                "productCode": prod["productCode"],
                "productName": prod["productName"],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "isActive": True,
                "updatedAt": now_dt,
            }
            self.db.iiot_product_master.update_one(
                {"productCode": prod["productCode"]},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
            self.db.products.update_one(
                {"product_code": prod["productCode"]},
                {
                    "$set": {
                        "product_name": prod["productName"],
                        "tenantId": TENANT_ID,
                        "plantId": PLANT_ID,
                        "updated_at": now_dt,
                    },
                    "$setOnInsert": {"product_code": prod["productCode"], "created_at": now_dt},
                },
                upsert=True,
            )
        logger.info(f"Synchronized {len(PRODUCT_DEFINITIONS)} compression product master records.")

    def sync_recipe_master(self) -> None:
        """Upsert standard compression recipes into iiot_recipe_master & iiot_batch_recipes."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        for r in RECIPE_DEFINITIONS:
            doc = {
                "recipeId": r["recipeId"],
                "recipeCode": r["recipeCode"],
                "recipeName": r["recipeName"],
                "productId": r.get("productId") or r["productCode"],
                "productCode": r["productCode"],
                "productName": r.get("productName", ""),
                "description": r.get("description", ""),
                "version": r.get("version", "1.0"),
                "associatedBatchSizes": r.get("associatedBatchSizes", ["500,000 Tabs", "1,000,000 Tabs"]),
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "isActive": True,
                "updatedAt": now_dt,
            }
            self.db.iiot_recipe_master.update_one(
                {"recipeCode": r["recipeCode"]},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
        logger.info(f"Synchronized {len(RECIPE_DEFINITIONS)} compression recipe master records.")

    def sync_recipe_management(self) -> None:
        """Upsert compression recipe management setpoints and parameters."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        for rm in RECIPE_MANAGEMENT_DEFINITIONS:
            eq_code = rm["equipmentCode"]
            doc = {
                "recipeManagementId": rm["recipeManagementId"],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "productId": rm.get("productId") or rm["productCode"],
                "productCode": rm["productCode"],
                "productName": rm.get("productName") or rm["productCode"],
                "recipeId": rm.get("recipeId") or rm["recipeCode"],
                "recipeCode": rm["recipeCode"],
                "recipeName": rm.get("recipeName") or rm["recipeCode"],
                "batchSize": rm.get("batchSize", "975,000 Tabs"),
                "equipmentId": eq_code,
                "equipmentCode": eq_code,
                "equipmentName": "Sejong 49D Compression Machine (MC081)",
                "parameterCode": rm["parameterCode"],
                "parameterName": rm["parameterName"],
                "unitOfMeasure": rm.get("unitOfMeasure", ""),
                "uom": rm.get("unitOfMeasure", ""),
                "targetSetpoint": rm.get("targetSetpoint"),
                "lowLimit": rm.get("lowLimit"),
                "highLimit": rm.get("highLimit"),
                "isActive": True,
                "updatedAt": now_dt,
            }
            self.db.iiot_recipe_management.update_one(
                {
                    "equipmentCode": eq_code,
                    "recipeCode": rm["recipeCode"],
                    "parameterCode": rm["parameterCode"],
                },
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
        logger.info(f"Synchronized {len(RECIPE_MANAGEMENT_DEFINITIONS)} compression recipe management limits.")

    def sync_all_master_data(self) -> None:
        """Synchronize complete compression master data."""
        self.sync_equipment_master()
        self.sync_critical_parameters()
        self.sync_product_master()
        self.sync_recipe_master()
        self.sync_recipe_management()

    def validate_and_sync_batch_master(
        self,
        *,
        product_code: str,
        product_name: str,
        recipe_code: Optional[str] = None,
        recipe_name: Optional[str] = None,
        batch_size: Optional[str] = None,
        equipment_code: Optional[str] = None,
        equipment_type: Optional[str] = None,
    ) -> None:
        """Sync product master, recipe master, and recipe management definitions during batch rollup."""
        if self.db is None or not product_code:
            return
        now_dt = datetime.now(timezone.utc)
        clean_prod = sanitize_code(product_code)
        prod_name = product_name or product_code

        # 1. Product Master Upsert
        prod_doc = {
            "productId": clean_prod or product_code,
            "productCode": product_code,
            "productName": prod_name,
            "tenantId": TENANT_ID,
            "plantId": PLANT_ID,
            "isActive": True,
            "updatedAt": now_dt,
        }
        self.db.iiot_product_master.update_one(
            {"productCode": product_code},
            {"$set": prod_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )
        self.db.products.update_one(
            {"product_code": product_code},
            {
                "$set": {
                    "product_name": prod_name,
                    "updated_at": now_dt,
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                },
                "$setOnInsert": {"product_code": product_code, "created_at": now_dt},
            },
            upsert=True,
        )

        # 2. Recipe Master Upsert
        if recipe_code:
            clean_rcp = sanitize_code(recipe_code)
            rcp_name = recipe_name or recipe_code
            rcp_doc = {
                "recipeId": clean_rcp or recipe_code,
                "recipeCode": recipe_code,
                "recipeName": rcp_name,
                "productId": clean_prod or product_code,
                "productCode": product_code,
                "productName": prod_name,
                "description": f"Recipe {rcp_name} for {prod_name}",
                "version": "1.0",
                "associatedBatchSizes": [batch_size] if batch_size else ["500,000 Tabs", "1,000,000 Tabs"],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "isActive": True,
                "updatedAt": now_dt,
            }
            self.db.iiot_recipe_master.update_one(
                {"recipeCode": recipe_code},
                {"$set": rcp_doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )

            # 3. Recipe Management Upsert for MC081
            eq_code = equipment_code or "MC081"
            if eq_code in CRITICAL_PARAMETERS:
                for param in CRITICAL_PARAMETERS[eq_code]:
                    rm_id = f"RCM-{eq_code}-{clean_rcp}-{param['code']}"
                    rm_doc = {
                        "recipeManagementId": rm_id,
                        "tenantId": TENANT_ID,
                        "plantId": PLANT_ID,
                        "productId": clean_prod or product_code,
                        "productCode": product_code,
                        "productName": prod_name,
                        "recipeId": clean_rcp or recipe_code,
                        "recipeCode": recipe_code,
                        "recipeName": rcp_name,
                        "batchSize": batch_size or "975,000 Tabs",
                        "equipmentId": eq_code,
                        "equipmentCode": eq_code,
                        "equipmentName": "Sejong 49D Compression Machine (MC081)",
                        "parameterCode": param["code"],
                        "parameterName": param["name"],
                        "unitOfMeasure": param.get("unit", ""),
                        "uom": param.get("unit", ""),
                        "targetSetpoint": param.get("base"),
                        "lowLimit": param.get("low"),
                        "highLimit": param.get("high"),
                        "isActive": True,
                        "updatedAt": now_dt,
                    }
                    self.db.iiot_recipe_management.update_one(
                        {
                            "equipmentCode": eq_code,
                            "recipeCode": recipe_code,
                            "parameterCode": param["code"],
                        },
                        {"$set": rm_doc, "$setOnInsert": {"createdAt": now_dt}},
                        upsert=True,
                    )

    def cascade_truncate_and_reset(
        self, target_equipment: Optional[List[str]] = None, reset_master: bool = False
    ) -> Dict[str, Any]:
        """Reset transactional data while keeping master records intact."""
        if self.db is None:
            return {"truncatedCollections": []}
        truncated = []
        existing = set(self.db.list_collection_names())
        cols_to_clear = [
            "iiot_batch_summary",
            "iiot_equipment_live_status",
            "iiot_batch_recipes",
            "iiot_batch_audit_trail",
            "iiot_ingested_events_registry",
            "iiot_ingestion_job_run",
        ]
        eqs = target_equipment or [e["equipmentCode"] for e in EQUIPMENT_DEFINITIONS]
        for eq in eqs:
            cols_to_clear.extend([
                f"iiot_ts_batch_{eq}",
                f"iiot_ts_alarm_{eq}",
                f"iiot_ts_audit_{eq}",
                f"iiot_ts_login_{eq}",
            ])
        for c in cols_to_clear:
            if c in existing:
                try:
                    self.db[c].delete_many({})
                    truncated.append(c)
                except Exception as exc:
                    logger.warning(f"Error truncating collection {c}: {exc}")
        if reset_master:
            self.sync_all_master_data()
        return {"truncatedCollections": truncated}
