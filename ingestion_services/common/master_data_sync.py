"""
Master Data Synchronization & Cascading Truncate Engine for ADAVIS IIoT Platform.
Synchronizes Equipment Master, Critical Parameters, Product Master, Recipe Master,
and Batch Associations from actual sample data across all 5 pieces of equipment:
- 4 API Equipment: RMG (MB003), FBD (MB004), Blender (MB005), Auto Coater (MB041)
- 1 File Equipment: Sejong 49D Compression (MC081)
"""

from __future__ import annotations

import logging
import re
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Set
from pymongo import MongoClient, UpdateOne

logger = logging.getLogger("iiot.master_data_sync")

DEFAULT_TENANT_ID = "TNT-0001"
DEFAULT_PLANT_ID = "PLNT-0001"
DEFAULT_BLOCK_ID = "BLK-PB1"

EQUIPMENT_LOCATION = {
    "MB003": ("AREA-GRAN", "ROOM-GRAN"),
    "MB004": ("AREA-GRAN", "ROOM-DRY"),
    "MB005": ("AREA-BLEND", "ROOM-BLEND"),
    "MC081": ("AREA-COMP", "ROOM-COMP"),
    # The supplied room master has no separate coating room.  Keep the coater in
    # the requested Coating Area and use a stable area-level room container.
    "MB041": ("AREA-COAT", "ROOM-COAT"),
}

# The 5 Genuine Equipment Specifications
EQUIPMENT_DEFINITIONS: List[Dict[str, Any]] = [
    {
        "equipmentId": "MB003",
        "equipmentCode": "MB003",
        "equipmentName": "Rapid Mixer Granulator",
        "equipmentType": "RMG",
        "assetId": "10094",
        "stageId": "STAGE-1",
        "stageName": "Granulation",
        "sequenceOrder": 1,
        "sourceType": "API",
        "model": "RMG 600L",
        "manufacturer": "G5 Pharma Machinery",
    },
    {
        "equipmentId": "MB004",
        "equipmentCode": "MB004",
        "equipmentName": "Fluid Bed Dryer",
        "equipmentType": "FBD",
        "assetId": "10110",
        "stageId": "STAGE-2",
        "stageName": "Drying",
        "sequenceOrder": 2,
        "sourceType": "API",
        "model": "FBD 300KG",
        "manufacturer": "G5 Pharma Machinery",
    },
    {
        "equipmentId": "MB005",
        "equipmentCode": "MB005",
        "equipmentName": "Octagonal Blender",
        "equipmentType": "BLE",
        "assetId": "10012",
        "stageId": "STAGE-3",
        "stageName": "Blending",
        "sequenceOrder": 3,
        "sourceType": "API",
        "model": "Octagonal Blender 1200L",
        "manufacturer": "G5 Pharma Machinery",
    },
    {
        "equipmentId": "MB041",
        "equipmentCode": "MB041",
        "equipmentName": "Auto Coater",
        "equipmentType": "COAT",
        "assetId": "10021",
        "stageId": "STAGE-4",
        "stageName": "Coating",
        "sequenceOrder": 4,
        "sourceType": "API",
        "model": "Auto Coater AC-48",
        "manufacturer": "G5 Pharma Machinery",
    },
    {
        "equipmentId": "MC081",
        "equipmentCode": "MC081",
        "equipmentName": "MC081 SEJONG 49D Compression Machine",
        "equipmentType": "COMP",
        "assetId": "MC081",
        "stageId": "STAGE-4",
        "stageName": "Compression",
        "sequenceOrder": 4,
        "sourceType": "FILE",
        "model": "Sejong 49D Rotary Tablet Press",
        "manufacturer": "Sejong Pharmatec",
    },
]

# Obsolete equipment codes to purge / deactivate
OBSOLETE_EQUIPMENT_CODES = {"G5RMG", "G5FBD", "G5OGB", "G5COAT", "MB040"}

# Critical Parameter Definitions for each equipment
CRITICAL_PARAMETERS_CONFIG: Dict[str, List[Dict[str, Any]]] = {
    "MB003": [
        {
            "parameterCode": "IMPELLER_SPEED",
            "parameterName": "Impeller Speed",
            "unit": "RPM",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 0.0,
            "maxLimit": 250.0,
            "targetValue": 150.0,
            "displaySequence": 1,
            "sourceTag": "IMPELLER_RPM",
        },
        {
            "parameterCode": "CHOPPER_SPEED",
            "parameterName": "Chopper Speed",
            "unit": "RPM",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 0.0,
            "maxLimit": 3000.0,
            "targetValue": 1500.0,
            "displaySequence": 2,
            "sourceTag": "CHOPPER_RPM",
        },
        {
            "parameterCode": "PRODUCT_TEMP",
            "parameterName": "Product Temperature",
            "unit": "°C",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 20.0,
            "maxLimit": 80.0,
            "targetValue": 35.0,
            "displaySequence": 3,
            "sourceTag": "TEMP_PRODUCT",
        },
        {
            "parameterCode": "CURRENT",
            "parameterName": "Impeller Current",
            "unit": "A",
            "dataType": "NUMERIC",
            "isCritical": False,
            "minLimit": 0.0,
            "maxLimit": 100.0,
            "targetValue": 35.0,
            "displaySequence": 4,
            "sourceTag": "CURRENT_AMPS",
        },
    ],
    "MB004": [
        {
            "parameterCode": "INLET_AIR_TEMP",
            "parameterName": "Inlet Air Temperature",
            "unit": "°C",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 40.0,
            "maxLimit": 80.0,
            "targetValue": 60.0,
            "displaySequence": 1,
            "sourceTag": "INLET_TEMP",
        },
        {
            "parameterCode": "EXHAUST_AIR_TEMP",
            "parameterName": "Exhaust Air Temperature",
            "unit": "°C",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 30.0,
            "maxLimit": 60.0,
            "targetValue": 45.0,
            "displaySequence": 2,
            "sourceTag": "EXHAUST_TEMP",
        },
        {
            "parameterCode": "PRODUCT_TEMP",
            "parameterName": "Product Temperature",
            "unit": "°C",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 30.0,
            "maxLimit": 65.0,
            "targetValue": 48.0,
            "displaySequence": 3,
            "sourceTag": "BED_TEMP",
        },
        {
            "parameterCode": "CFM",
            "parameterName": "Air Flow Rate",
            "unit": "CFM",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 500.0,
            "maxLimit": 3500.0,
            "targetValue": 2200.0,
            "displaySequence": 4,
            "sourceTag": "AIR_FLOW_CFM",
        },
        {
            "parameterCode": "FILTER_BAG_PRESSURE",
            "parameterName": "Filter Bag Differential Pressure",
            "unit": "mmWC",
            "dataType": "NUMERIC",
            "isCritical": False,
            "minLimit": 10.0,
            "maxLimit": 150.0,
            "targetValue": 60.0,
            "displaySequence": 5,
            "sourceTag": "FILTER_DP",
        },
    ],
    "MB005": [
        {
            "parameterCode": "BLENDER_SPEED",
            "parameterName": "Blender Speed",
            "unit": "RPM",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 0.0,
            "maxLimit": 25.0,
            "targetValue": 12.0,
            "displaySequence": 1,
            "sourceTag": "BLENDER_RPM",
        },
        {
            "parameterCode": "PROCESS_TIME",
            "parameterName": "Blending Process Time",
            "unit": "min",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 5.0,
            "maxLimit": 120.0,
            "targetValue": 30.0,
            "displaySequence": 2,
            "sourceTag": "PROCESS_TIME_MIN",
        },
        {
            "parameterCode": "TOTAL_REVOLUTIONS",
            "parameterName": "Total Revolutions",
            "unit": "rev",
            "dataType": "NUMERIC",
            "isCritical": False,
            "minLimit": 50.0,
            "maxLimit": 2000.0,
            "targetValue": 360.0,
            "displaySequence": 3,
            "sourceTag": "TOTAL_REV",
        },
    ],
    "MB041": [
        {
            "parameterCode": "PAN_SPEED",
            "parameterName": "Pan Speed",
            "unit": "RPM",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 1.0,
            "maxLimit": 15.0,
            "targetValue": 8.0,
            "displaySequence": 1,
            "sourceTag": "PAN_RPM",
        },
        {
            "parameterCode": "INLET_AIR_TEMP",
            "parameterName": "Inlet Air Temperature",
            "unit": "°C",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 40.0,
            "maxLimit": 75.0,
            "targetValue": 60.0,
            "displaySequence": 2,
            "sourceTag": "INLET_AIR_TEMP",
        },
        {
            "parameterCode": "EXHAUST_AIR_TEMP",
            "parameterName": "Exhaust Air Temperature",
            "unit": "°C",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 35.0,
            "maxLimit": 60.0,
            "targetValue": 46.0,
            "displaySequence": 3,
            "sourceTag": "EXHAUST_AIR_TEMP",
        },
        {
            "parameterCode": "BED_TEMP",
            "parameterName": "Tablet Bed Temperature",
            "unit": "°C",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 35.0,
            "maxLimit": 55.0,
            "targetValue": 44.0,
            "displaySequence": 4,
            "sourceTag": "BED_TEMP",
        },
        {
            "parameterCode": "SPRAY_RATE",
            "parameterName": "Spray Rate",
            "unit": "g/min",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 50.0,
            "maxLimit": 300.0,
            "targetValue": 150.0,
            "displaySequence": 5,
            "sourceTag": "SPRAY_RATE_GPM",
        },
        {
            "parameterCode": "ATOMIZATION_PRESSURE",
            "parameterName": "Atomization Air Pressure",
            "unit": "bar",
            "dataType": "NUMERIC",
            "isCritical": False,
            "minLimit": 1.0,
            "maxLimit": 4.0,
            "targetValue": 2.5,
            "displaySequence": 6,
            "sourceTag": "ATOM_AIR_BAR",
        },
    ],
    "MC081": [
        {
            "parameterCode": "MAIN_PRESSURE",
            "parameterName": "Main Compression Force",
            "unit": "kN",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 0.0,
            "maxLimit": 100.0,
            "targetValue": 8.65,
            "displaySequence": 1,
            "sourceTag": "MainPressure",
        },
        {
            "parameterCode": "PRE_PRESSURE",
            "parameterName": "Pre Compression Force",
            "unit": "kN",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 0.0,
            "maxLimit": 30.0,
            "targetValue": 2.1,
            "displaySequence": 2,
            "sourceTag": "PrePressure",
        },
        {
            "parameterCode": "TURRET_SPEED",
            "parameterName": "Turret / Disk Speed",
            "unit": "RPM",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 5.0,
            "maxLimit": 70.0,
            "targetValue": 23.0,
            "displaySequence": 3,
            "sourceTag": "DiskSpeed",
        },
        {
            "parameterCode": "FEEDER_SPEED",
            "parameterName": "Feeder Speed",
            "unit": "RPM",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 5.0,
            "maxLimit": 80.0,
            "targetValue": 12.0,
            "displaySequence": 4,
            "sourceTag": "FeederRpm",
        },
        {
            "parameterCode": "FILL_DEPTH",
            "parameterName": "Filling Depth",
            "unit": "mm",
            "dataType": "NUMERIC",
            "isCritical": False,
            "minLimit": 1.0,
            "maxLimit": 20.0,
            "targetValue": 11.2,
            "displaySequence": 5,
            "sourceTag": "FillingDepth",
        },
        {
            "parameterCode": "TABLET_THICKNESS",
            "parameterName": "Tablet Thickness",
            "unit": "mm",
            "dataType": "NUMERIC",
            "isCritical": False,
            "minLimit": 1.0,
            "maxLimit": 10.0,
            "targetValue": 4.5,
            "displaySequence": 6,
            "sourceTag": "TabletThickness",
        },
        {
            "parameterCode": "TABLET_WEIGHT",
            "parameterName": "Tablet Weight",
            "unit": "mg",
            "dataType": "NUMERIC",
            "isCritical": True,
            "minLimit": 50.0,
            "maxLimit": 1000.0,
            "targetValue": 350.0,
            "displaySequence": 7,
            "sourceTag": "TabletWeight",
        },
    ],
}


def normalize_batch_size_str(val: Any) -> str:
    """Normalize batch size into standard format e.g. '248.640 KG' or '975,000 Tabs'."""
    if val is None:
        return "1000 KG"
    s = str(val).strip()
    if not s or s.upper() == "NULL" or s.upper() == "NONE":
        return "1000 KG"
    
    # Check if number already has unit
    if re.search(r"[a-zA-Z]", s):
        parts = s.split()
        if len(parts) >= 2:
            try:
                num = float(parts[0].replace(",", ""))
                unit = parts[1].upper()
                if "TAB" in unit:
                    return f"{int(num):,} Tabs"
                elif "KG" in unit:
                    return f"{num:.3f}".rstrip("0").rstrip(".") + " KG"
            except Exception:
                pass
        return s.strip()
    
    try:
        num = float(s.replace(",", ""))
        if num > 10000:
            # Tablet count
            return f"{int(num):,} Tabs"
        else:
            return f"{num:.3f}".rstrip("0").rstrip(".") + " KG"
    except Exception:
        return s


def sanitize_code(s: str) -> str:
    """Creates a clean identifier string."""
    cleaned = re.sub(r"[^A-Za-z0-9]+", "-", str(s).strip().upper())
    return cleaned.strip("-")


class MasterDataSyncManager:
    """
    Manages synchronization of Equipment Master, Critical Parameters,
    Product Master, Recipe Master, and Batch Associations.
    Also handles cascading Truncate & Load resets.
    """

    def __init__(self, db: Any) -> None:
        self.db = db

    def sync_equipment_master(self) -> List[str]:
        """
        Synchronizes all 5 genuine equipment into iiot_equipment_master and purges/deactivates obsolete codes.
        """
        if self.db is None:
            return []

        now_dt = datetime.now(timezone.utc)
        synced_codes: List[str] = []
        self.sync_topology()

        # 1. Upsert 5 genuine equipment
        for eq in EQUIPMENT_DEFINITIONS:
            eq_code = eq["equipmentCode"]
            doc = {
                "equipmentId": eq["equipmentId"],
                "equipmentCode": eq_code,
                "equipmentName": eq["equipmentName"],
                "equipmentType": eq["equipmentType"],
                "assetId": eq["assetId"],
                "stageId": eq.get("stageId", "STAGE-1"),
                "stageName": eq.get("stageName", "Stage"),
                "sequenceOrder": eq.get("sequenceOrder", 1),
                "model": eq.get("model", ""),
                "manufacturer": eq.get("manufacturer", ""),
                "sourceType": eq["sourceType"],
                "plantId": DEFAULT_PLANT_ID,
                "tenantId": DEFAULT_TENANT_ID,
                "blockId": DEFAULT_BLOCK_ID,
                "areaId": EQUIPMENT_LOCATION[eq_code][0],
                "roomId": EQUIPMENT_LOCATION[eq_code][1],
                "status": "ACTIVE",
                "isActive": True,
                "updatedAt": now_dt,
            }
            self.db.iiot_equipment_master.update_one(
                {"equipmentCode": eq_code},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
            synced_codes.append(eq_code)
            logger.info(f"Master Data: Synced Equipment '{eq_code}' ({eq['equipmentName']})")

        # 2. Remove obsolete equipment records from both historical spellings of
        # the equipment-master collection. The dependent mappings are purged below.
        for collection_name in ("iiot_equipment_master", "iiot_equiment_master"):
            self.db[collection_name].delete_many({"$or": [
                {"equipmentCode": {"$in": list(OBSOLETE_EQUIPMENT_CODES)}},
                {"equipmentId": {"$in": list(OBSOLETE_EQUIPMENT_CODES)}},
            ]})

        obsolete_filter = {"$or": [
            {"equipmentCode": {"$in": list(OBSOLETE_EQUIPMENT_CODES)}},
            {"equipmentId": {"$in": list(OBSOLETE_EQUIPMENT_CODES)}},
        ]}
        for collection_name in (
            "iiot_equipment_critical_parameters",
            "iiot_equipment_critical_parameters_limit",
            "iiot_recipe_management",
            "iiot_batch_recipes",
        ):
            self.db[collection_name].delete_many(obsolete_filter)

        return synced_codes

    def sync_topology(self) -> None:
        """Synchronize the single-plant Aurobindo topology used by IIoT master data."""
        now_dt = datetime.now(timezone.utc)
        self.db.mdm_tenants.update_one(
            {"tenantId": DEFAULT_TENANT_ID},
            {"$set": {"companyName": "Aurobindo Pharma Limited (APL)", "companyCode": "APL", "isActive": True, "updatedAt": now_dt}},
            upsert=True,
        )
        self.db.mdm_plants.delete_many({"tenantId": DEFAULT_TENANT_ID, "plantId": {"$ne": DEFAULT_PLANT_ID}})
        self.db.mdm_plants.update_one(
            {"plantId": DEFAULT_PLANT_ID},
            {"$set": {"tenantId": DEFAULT_TENANT_ID, "plantName": "Unit-VII (OSD Formulations)", "plantCode": "UNIT-07", "type": "Manufacturing", "timezone": "Asia/Kolkata", "isActive": True, "updatedAt": now_dt}},
            upsert=True,
        )
        self.db.mdm_blocks.delete_many({"tenantId": DEFAULT_TENANT_ID, "plantId": DEFAULT_PLANT_ID, "blockId": {"$ne": DEFAULT_BLOCK_ID}})
        self.db.mdm_blocks.update_one(
            {"blockId": DEFAULT_BLOCK_ID},
            {"$set": {"tenantId": DEFAULT_TENANT_ID, "plantId": DEFAULT_PLANT_ID, "blockCode": "PB1", "blockName": "Production Block 1", "displayOrder": 1, "isActive": True, "updatedAt": now_dt}},
            upsert=True,
        )
        areas = [
            ("AREA-GRAN", "GRAN", "Granulation Area"),
            ("AREA-BLEND", "BLEND", "Blending Area"),
            ("AREA-COMP", "COMP", "Compression Area"),
            ("AREA-COAT", "COAT", "Coating Area"),
        ]
        rooms = [
            ("ROOM-GRAN", "AREA-GRAN", "GRAN-RM", "Granulation Room"),
            ("ROOM-DRY", "AREA-GRAN", "DRY-RM", "Drying Room"),
            ("ROOM-BLEND", "AREA-BLEND", "BLEND-RM", "Blending Room"),
            ("ROOM-COMP", "AREA-COMP", "COMP-RM", "Compression Room"),
            ("ROOM-COAT", "AREA-COAT", "COAT-RM", "Coating Room"),
        ]
        self.db.mdm_areas.delete_many({"tenantId": DEFAULT_TENANT_ID, "plantId": DEFAULT_PLANT_ID})
        self.db.mdm_rooms.delete_many({"tenantId": DEFAULT_TENANT_ID, "plantId": DEFAULT_PLANT_ID})
        self.db.mdm_areas.insert_many([
            {"areaId": area_id, "tenantId": DEFAULT_TENANT_ID, "plantId": DEFAULT_PLANT_ID, "blockId": DEFAULT_BLOCK_ID, "areaCode": code, "areaName": name, "displayOrder": index + 1, "isActive": True, "createdAt": now_dt, "updatedAt": now_dt}
            for index, (area_id, code, name) in enumerate(areas)
        ])
        self.db.mdm_rooms.insert_many([
            {"roomId": room_id, "tenantId": DEFAULT_TENANT_ID, "plantId": DEFAULT_PLANT_ID, "areaId": area_id, "roomCode": code, "roomName": name, "isActive": True, "createdAt": now_dt, "updatedAt": now_dt}
            for room_id, area_id, code, name in rooms
        ])

    def sync_critical_parameters(self) -> int:
        """
        Synchronizes critical parameter definitions and limits for all 5 equipment.
        """
        if self.db is None:
            return 0

        now_dt = datetime.now(timezone.utc)
        count = 0

        obsolete_filter = {"$or": [
            {"equipmentCode": {"$in": list(OBSOLETE_EQUIPMENT_CODES)}},
            {"equipmentId": {"$in": list(OBSOLETE_EQUIPMENT_CODES)}},
        ]}
        self.db.iiot_equipment_critical_parameters.delete_many(obsolete_filter)
        self.db.iiot_equipment_critical_parameters_limit.delete_many(obsolete_filter)
        self.db.iiot_recipe_management.delete_many(obsolete_filter)

        for eq_code, params in CRITICAL_PARAMETERS_CONFIG.items():
            for p in params:
                param_code = p["parameterCode"]
                param_id = f"CP-{eq_code}-{param_code}"
                limit_id = f"CPL-{eq_code}-{param_code}"

                # Parameter Master
                param_doc = {
                    "parameterId": param_id,
                    "equipmentId": eq_code,
                    "equipmentCode": eq_code,
                    "parameterCode": param_code,
                    "parameterName": p["parameterName"],
                    "unit": p["unit"],
                    "dataType": p["dataType"],
                    "isCritical": p["isCritical"],
                    "criticalParameter": p["isCritical"],
                    "sourceTag": p.get("sourceTag", param_code),
                    "displaySequence": p.get("displaySequence", 1),
                    "tenantId": DEFAULT_TENANT_ID,
                    "plantId": DEFAULT_PLANT_ID,
                    "isActive": True,
                    "updatedAt": now_dt,
                }
                self.db.iiot_equipment_critical_parameters.update_one(
                    {"parameterId": param_id},
                    {"$set": param_doc, "$setOnInsert": {"createdAt": now_dt}},
                    upsert=True,
                )

                # Parameter Limits
                limit_doc = {
                    "parameterLimitId": limit_id,
                    "parameterId": param_id,
                    "equipmentId": eq_code,
                    "equipmentCode": eq_code,
                    "parameterCode": param_code,
                    "minLimit": p.get("minLimit", 0.0),
                    "maxLimit": p.get("maxLimit", 100.0),
                    "targetValue": p.get("targetValue", 50.0),
                    "targetSetpoint": p.get("targetValue"),
                    "setPoint": p.get("targetValue"),
                    "lowLimit": p.get("minLimit"),
                    "highLimit": p.get("maxLimit"),
                    "lowerCriticalLimit": p.get("minLimit"),
                    "upperCriticalLimit": p.get("maxLimit"),
                    "unit": p["unit"],
                    "tenantId": DEFAULT_TENANT_ID,
                    "plantId": DEFAULT_PLANT_ID,
                    "isActive": True,
                    "updatedAt": now_dt,
                }
                self.db.iiot_equipment_critical_parameters_limit.update_one(
                    {"parameterLimitId": limit_id},
                    {"$set": limit_doc, "$setOnInsert": {"createdAt": now_dt}},
                    upsert=True,
                )

                recipe_code = f"RCP-{eq_code}-STANDARD"
                recipe_management_doc = {
                    "recipeManagementId": f"RCM-{eq_code}-{param_code}",
                    "tenantId": DEFAULT_TENANT_ID,
                    "plantId": DEFAULT_PLANT_ID,
                    "recipeId": recipe_code,
                    "recipeCode": recipe_code,
                    "recipeName": f"{eq_code} Standard Operating Recipe",
                    "equipmentId": eq_code,
                    "equipmentCode": eq_code,
                    "equipmentName": next(e["equipmentName"] for e in EQUIPMENT_DEFINITIONS if e["equipmentCode"] == eq_code),
                    "parameterId": param_id,
                    "parameterCode": param_code,
                    "parameterName": p["parameterName"],
                    "unitOfMeasure": p["unit"],
                    "uom": p["unit"],
                    "targetSetpoint": p.get("targetValue"),
                    "setPoint": p.get("targetValue"),
                    "lowLimit": p.get("minLimit"),
                    "highLimit": p.get("maxLimit"),
                    "isActive": True,
                    "updatedAt": now_dt,
                }
                self.db.iiot_recipe_management.update_one(
                    {"recipeManagementId": recipe_management_doc["recipeManagementId"]},
                    {"$set": recipe_management_doc, "$setOnInsert": {"createdAt": now_dt}},
                    upsert=True,
                )
                count += 1

        logger.info(f"Master Data: Synced {count} Critical Parameters & Limits across 5 equipment")
        return count

    def validate_and_sync_batch_master(
        self,
        *,
        product_code: Optional[str],
        product_name: Optional[str],
        recipe_code: Optional[str] = None,
        recipe_name: Optional[str] = None,
        batch_size: Optional[Any] = None,
        equipment_code: str,
        equipment_type: str,
    ) -> Dict[str, Any]:
        """
        Validates Product -> Recipe -> Batch Size -> Equipment hierarchy.
        Auto-creates or updates Product and Recipe Master if missing.
        """
        if self.db is None:
            return {}

        now_dt = datetime.now(timezone.utc)

        # 1. Resolve & normalize Product
        p_name = (product_name or product_code or "UNKNOWN PRODUCT").strip()
        p_code = (product_code or sanitize_code(p_name)).strip()
        product_id = f"PRD-{sanitize_code(p_code)}"

        # Upsert into iiot_product_master and products
        product_doc = {
            "productId": product_id,
            "productCode": p_code,
            "productName": p_name,
            "productCategory": "Tablets",
            "tenantId": DEFAULT_TENANT_ID,
            "plantId": DEFAULT_PLANT_ID,
            "equipmentId": equipment_code,
            "isActive": True,
            "updatedAt": now_dt,
        }
        self.db.iiot_product_master.update_one(
            {"tenantId": DEFAULT_TENANT_ID, "productId": product_id},
            {"$set": product_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )
        self.db.products.update_one(
            {"product_code": p_code},
            {"$set": {"product_name": p_name, "updated_at": now_dt}, "$setOnInsert": {"product_code": p_code, "created_at": now_dt}},
            upsert=True,
        )

        # 2. Normalize Batch Size
        norm_batch_size = normalize_batch_size_str(batch_size)

        # 3. Resolve & normalize Recipe
        if not recipe_name or recipe_name.strip().upper() in ("NULL", "NONE", ""):
            stage_desc = {
                "RMG": "Granulation",
                "FBD": "Drying",
                "BLE": "Blending",
                "COAT": "Coating",
                "COMP": "Compression",
            }.get(equipment_type, equipment_type)
            r_name = f"{p_name} {stage_desc} Recipe"
        else:
            r_name = recipe_name.strip()

        if not recipe_code or recipe_code.strip().upper() in ("NULL", "NONE", ""):
            r_code = f"RCP-{sanitize_code(p_code)}-{equipment_type}"
        else:
            r_code = recipe_code.strip()

        recipe_id = r_code

        # 4. Upsert Recipe Master with associatedBatchSizes
        existing_recipe = self.db.iiot_recipe_master.find_one({"recipeCode": r_code})
        associated_sizes: List[str] = []
        if existing_recipe:
            associated_sizes = list(existing_recipe.get("associatedBatchSizes") or [])

        if norm_batch_size and norm_batch_size not in associated_sizes:
            associated_sizes.append(norm_batch_size)

        recipe_master_doc = {
            "recipeId": recipe_id,
            "recipeCode": r_code,
            "recipeName": r_name,
            "productId": product_id,
            "productCode": p_code,
            "productName": p_name,
            "equipmentId": equipment_code,
            "equipmentCode": equipment_code,
            "equipmentType": equipment_type,
            "tenantId": DEFAULT_TENANT_ID,
            "plantId": DEFAULT_PLANT_ID,
            "associatedBatchSizes": associated_sizes,
            "description": f"Standard recipe for {p_name} on {equipment_code} ({equipment_type})",
            "version": "1.0",
            "isActive": True,
            "updatedAt": now_dt,
        }
        self.db.iiot_recipe_master.update_one(
            {"recipeCode": r_code},
            {"$set": recipe_master_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )

        logger.info(
            f"Master Data Sync: Validated Product '{p_name}' ({p_code}) -> Recipe '{r_name}' ({r_code}) -> Size '{norm_batch_size}' -> Equipment '{equipment_code}'"
        )

        return {
            "productId": product_id,
            "productCode": p_code,
            "productName": p_name,
            "recipeId": recipe_id,
            "recipeCode": r_code,
            "recipeName": r_name,
            "batchSize": norm_batch_size,
            "equipmentCode": equipment_code,
            "equipmentType": equipment_type,
        }

    def cascade_truncate_and_reset(self, target_equipment: Optional[List[str]] = None, reset_master: bool = False) -> Dict[str, Any]:
        """
        Performs strict parent-child cascading deletion in exact dependency order:
        1. Workflow History, Audit Trail, Instances
        2. Generated PDF Documents & DMS files
        3. Equipment Time-Series streams (batch, alarm, audit, login)
        4. Ingestion Checkpoints, Job Runs, Ingested Event Registries
        5. Batch Summary & Multi-Stage Execution Records
        6. Equipment Live Status
        7. If reset_master is True: Recipe Master, Product Master, Parameters, Equipment Master.
        """
        if self.db is None:
            return {"status": "NO_DATABASE"}

        all_equipment = target_equipment or ["MB003", "MB004", "MB005", "MB041", "MC081", "G5RMG", "G5FBD", "G5OGB", "G5COAT", "MB040"]
        existing_cols = set(self.db.list_collection_names())
        truncated_cols: List[str] = []

        logger.info("================================================================")
        logger.info("Executing TRUNCATE AND LOAD Reset in strict dependency order")
        logger.info("================================================================")

        # 1. Workflow and Approval instances & history
        workflow_cols = [
            "iiot_workflow_action_history",
            "iiot_workflow_audit_trail",
            "iiot_workflow_instances",
        ]
        for col_name in workflow_cols:
            if col_name in existing_cols:
                self.db[col_name].delete_many({})
                truncated_cols.append(col_name)

        # 2. Documents & Print trails
        doc_cols = ["iiot_generated_documents", "dms_documents"]
        for col_name in doc_cols:
            if col_name in existing_cols:
                self.db[col_name].delete_many({})
                truncated_cols.append(col_name)

        # 3. Time-Series streams for all target equipment
        for eq in all_equipment:
            ts_cols = [
                f"iiot_ts_batch_{eq}",
                f"iiot_ts_alarm_{eq}",
                f"iiot_ts_audit_{eq}",
                f"iiot_ts_login_{eq}",
            ]
            for col_name in ts_cols:
                if col_name in existing_cols:
                    self.db[col_name].delete_many({})
                    truncated_cols.append(col_name)

        # 4. Ingestion Registry, Checkpoints, and Live Status
        ingest_cols = [
            "iiot_ingested_events_registry",
            "iiot_ingestion_checkpoint",
            "iiot_ingestion_job_run",
            "iiot_equipment_live_status",
            "iiot_batch_summary",
            "iiot_batch_recipes",
            "iiot_batch_audit_trail",
        ]
        for col_name in ingest_cols:
            if col_name in existing_cols:
                self.db[col_name].delete_many({})
                truncated_cols.append(col_name)

        # 5. Master Data if reset_master is True
        if reset_master:
            master_cols = [
                "iiot_recipe_master",
                "iiot_product_master",
                "products",
                "iiot_equipment_critical_parameters_limit",
                "iiot_equipment_critical_parameters",
                "iiot_equipment_master",
            ]
            for col_name in master_cols:
                if col_name in existing_cols:
                    self.db[col_name].delete_many({})
                    truncated_cols.append(col_name)

        logger.info(f"Cascading Reset complete. Truncated collections ({len(truncated_cols)}): {truncated_cols}")

        # Always re-initialize equipment master and critical parameters
        self.sync_equipment_master()
        self.sync_critical_parameters()

        return {
            "status": "COMPLETED",
            "truncatedCollections": truncated_cols,
            "resetMaster": reset_master,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
