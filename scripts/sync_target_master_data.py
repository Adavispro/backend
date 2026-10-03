#!/usr/bin/env python3
"""Synchronize MongoDB master collections and Redis live cache for all 5 target equipments.

Target Equipments:
- MB003 (RMG, AssetId: 10094, Batch: AGO0026016/01, Product: LAMOTRIGINE)
- MB004 (FBD, AssetId: 10110, Batch: AGO0026016/1B, Product: LAMOTRIGINE)
- MB005 (Blender, AssetId: 10095, Batch: AGO0026015/01, Product: LAMOTRIGINE)
- MB040 (Compression, AssetId: 10040, Batch: Pb1 Mb Compression/01, Product: LAMOTRIGINE)
- MB041 (Coating Machine, AssetId: 10141, Batch: PED26009/NA, Product: PAROXETINE USP 40 mg)

This script is idempotent — safe to run multiple times.
"""

from __future__ import annotations

import json
import os
import sys
from datetime import datetime, timezone

import pymongo

try:
    import redis
except ImportError:
    redis = None

MONGO_URI = os.getenv("MONGO_URI", "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin")
DB_NAME = os.getenv("DB_NAME", "adavis_platform")
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "8379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "Redis123!")

DEFAULT_PASSWORD_HASH = "$2a$10$pzWt1lCtyuKoDTX3cv6ICOvlchKgpk/OAvzZXFbiT6HrodBFtyFxe"

TENANT_ID = "TNT-0001"
PLANT_ID = "PLNT-0001"
BLOCK_ID = "BLK-0001"
AREA_ID = "AREA-0001"
ROOM_ID = "ROOM-0001"


def _upsert(collection, filter_doc: dict, update_doc: dict, now_dt: datetime) -> None:
    """Idempotent upsert: $set existing fields, $setOnInsert createdAt."""
    collection.update_one(
        filter_doc,
        {
            "$set": {**update_doc, "updatedAt": now_dt},
            "$setOnInsert": {"createdAt": now_dt},
        },
        upsert=True,
    )


def sync_all():
    client = pymongo.MongoClient(MONGO_URI)
    db = client[DB_NAME]
    now_dt = datetime.now(timezone.utc)

    # Redis Client
    r_client = None
    if redis is not None:
        try:
            r_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, password=REDIS_PASSWORD, decode_responses=True, socket_timeout=3)
            r_client.ping()
        except Exception:
            r_client = None

    print(f"Connecting to MongoDB at {MONGO_URI}, DB: {DB_NAME}...")

    # ==========================================
    # 1. Equipment Master (all 5 equipment)
    # ==========================================
    equipments = [
        {
            "equipmentId": "MB003",
            "equipmentCode": "MB003",
            "equipmentName": "Rapid Mixer Granulator",
            "assetId": "10094",
            "lineId": "PB1",
            "plantId": PLANT_ID,
            "blockId": BLOCK_ID,
            "areaId": AREA_ID,
            "roomId": ROOM_ID,
            "tenantId": TENANT_ID,
            "stage": "RMG / Granulation",
            "block": "PB1",
            "area": "MODULE-B",
            "make": "BECTOCHEM",
            "model": "FX5U 32 MR",
            "plc": "MITSUBISHI FX5U 32 MR",
            "plcModel": "MITSUBISHI FX5U 32 MR",
            "dataType": "SQL",
            "hmiIpc": "Beijer",
            "vendor": "Retron21 / Anmeda",
            "equipmentType": "RMG",
            "equipmentTypeName": "Rapid Mixer Granulator",
            "isActive": True,
            "isDeleted": False,
            "hierarchy": {
                "plant": PLANT_ID,
                "block": BLOCK_ID,
                "area": AREA_ID,
                "room": ROOM_ID,
                "fullPath": f"{PLANT_ID}/{BLOCK_ID}/{AREA_ID}/{ROOM_ID}/MB003",
            },
            "lastBatchNo": "AGO0026016",
            "lastLotNo": "01",
            "sampleProduct": "LAMOTRIGINE",
            "sampleBatch": "AGO0026016",
            "lot": "Lot 01",
        },
        {
            "equipmentId": "MB004",
            "equipmentCode": "MB004",
            "equipmentName": "Fluid Bed Dryer",
            "assetId": "10110",
            "lineId": "PB1",
            "plantId": PLANT_ID,
            "blockId": BLOCK_ID,
            "areaId": AREA_ID,
            "roomId": ROOM_ID,
            "tenantId": TENANT_ID,
            "stage": "FBD / Drying",
            "block": "PB1",
            "area": "MODULE-B",
            "make": "ALLIANCE",
            "model": "Fx5U 32 MR",
            "plc": "MITSUBISHI Fx5U 32 MR",
            "plcModel": "MITSUBISHI Fx5U 32 MR",
            "dataType": "SQL",
            "hmiIpc": "Beijer",
            "vendor": "Retron21 / Anmeda",
            "equipmentType": "FBD",
            "equipmentTypeName": "Fluid Bed Dryer",
            "isActive": True,
            "isDeleted": False,
            "hierarchy": {
                "plant": PLANT_ID,
                "block": BLOCK_ID,
                "area": AREA_ID,
                "room": ROOM_ID,
                "fullPath": f"{PLANT_ID}/{BLOCK_ID}/{AREA_ID}/{ROOM_ID}/MB004",
            },
            "lastBatchNo": "AGO0026016",
            "lastLotNo": "1B",
            "sampleProduct": "LAMOTRIGINE",
            "sampleBatch": "AGO0026016",
            "lot": "Lot 1B",
        },
        {
            "equipmentId": "MB005",
            "equipmentCode": "MB005",
            "equipmentName": "Blender",
            "assetId": "10095",
            "lineId": "PB1",
            "plantId": PLANT_ID,
            "blockId": BLOCK_ID,
            "areaId": AREA_ID,
            "roomId": ROOM_ID,
            "tenantId": TENANT_ID,
            "stage": "BLE / Blending",
            "block": "PB1",
            "area": "MODULE-B",
            "make": "BECTOCHEM",
            "model": "Fx3U 32 MR",
            "plc": "MITSUBISHI Fx3U 32 MR",
            "plcModel": "MITSUBISHI Fx3U 32 MR",
            "dataType": "SQL",
            "hmiIpc": "Beijer",
            "vendor": "Retron21 / Anmeda",
            "equipmentType": "BLE",
            "equipmentTypeName": "Blender",
            "isActive": True,
            "isDeleted": False,
            "hierarchy": {
                "plant": PLANT_ID,
                "block": BLOCK_ID,
                "area": AREA_ID,
                "room": ROOM_ID,
                "fullPath": f"{PLANT_ID}/{BLOCK_ID}/{AREA_ID}/{ROOM_ID}/MB005",
            },
            "lastBatchNo": "AGO0026015",
            "lastLotNo": "01",
            "sampleProduct": "LAMOTRIGINE",
            "sampleBatch": "AGO0026015",
        },
        {
            "equipmentId": "MB040",
            "equipmentCode": "MB040",
            "equipmentName": "Compression Machine",
            "assetId": "10040",
            "lineId": "PB1",
            "plantId": PLANT_ID,
            "blockId": BLOCK_ID,
            "areaId": AREA_ID,
            "roomId": ROOM_ID,
            "tenantId": TENANT_ID,
            "stage": "COMP / Compression",
            "block": "PB1",
            "area": "MODULE-B",
            "make": "SEJONG",
            "model": "CJ1G CPU 44H",
            "plc": "OMRON Sysmac CJ1G CPU 44H",
            "plcModel": "OMRON Sysmac CJ1G CPU 44H",
            "dataType": "MS ACCESS + Excel",
            "hmiIpc": "Proface IPC",
            "vendor": "Lab view / Sejong",
            "equipmentType": "COMP",
            "equipmentTypeName": "Compression Machine",
            "isActive": True,
            "isDeleted": False,
            "hierarchy": {
                "plant": PLANT_ID,
                "block": BLOCK_ID,
                "area": AREA_ID,
                "room": ROOM_ID,
                "fullPath": f"{PLANT_ID}/{BLOCK_ID}/{AREA_ID}/{ROOM_ID}/MB040",
            },
            "lastBatchNo": "Pb1 Mb Compression",
            "lastLotNo": "01",
            "sampleBatch": "Pb1 Mb Compression Batch Data",
        },
        {
            "equipmentId": "MB041",
            "equipmentCode": "MB041",
            "equipmentName": "Coating Machine",
            "assetId": "10141",
            "lineId": "PB1",
            "plantId": PLANT_ID,
            "blockId": BLOCK_ID,
            "areaId": "COATING-MODULE-B",
            "roomId": ROOM_ID,
            "tenantId": TENANT_ID,
            "stage": "COAT / Coating",
            "block": "PB1",
            "area": "COATING MODULE-B",
            "make": "GANSONS",
            "model": "Fx3U 32 MR",
            "plc": "MITSUBISHI Fx3U 32 MR",
            "plcModel": "MITSUBISHI Fx3U 32 MR",
            "dataType": "SQL",
            "hmiIpc": "Beijer",
            "vendor": "Retron21 / Anmeda",
            "equipmentType": "COAT",
            "equipmentTypeName": "Coating Machine",
            "isActive": True,
            "isDeleted": False,
            "hierarchy": {
                "plant": PLANT_ID,
                "block": BLOCK_ID,
                "area": "COATING-MODULE-B",
                "room": ROOM_ID,
                "fullPath": f"{PLANT_ID}/{BLOCK_ID}/COATING-MODULE-B/{ROOM_ID}/MB041",
            },
            "lastBatchNo": "PED26009",
            "lastLotNo": "NA",
            "sampleProduct": "PAROXETINE USP 40mg",
            "sampleBatch": "PED26009",
        },
    ]

    for col_name in ("iiot_equipment_master", "iiot_equiment_master"):
        for eq in equipments:
            _upsert(db[col_name], {"equipmentId": eq["equipmentId"]}, eq, now_dt)
    print(f"✓ Upserted {len(equipments)} equipment records into iiot_equipment_master (idempotent).")

    # ==========================================
    # 2. Critical Parameters & Limits
    # ==========================================
    critical_params = [
        # RMG (MB003)
        {"equipmentId": "MB003", "parameterId": "agAmps",      "parameterName": "Impeller Current",      "unitOfMeasure": "A",   "idealValue": 22.4, "lowWarningValue": 18.0, "lowCriticalValue": 15.0, "highWarningValue": 26.7,  "highCriticalValue": 30.0},
        {"equipmentId": "MB003", "parameterId": "chpAmps",     "parameterName": "Chopper Current",       "unitOfMeasure": "A",   "idealValue": 4.5,  "lowWarningValue": 3.5,  "lowCriticalValue": 2.0,  "highWarningValue": 6.2,   "highCriticalValue": 7.5},
        {"equipmentId": "MB003", "parameterId": "pumpRpm",     "parameterName": "Pump Speed",            "unitOfMeasure": "RPM", "idealValue": 60.0, "lowWarningValue": 10.0, "lowCriticalValue": 0.0,  "highWarningValue": 70.0,  "highCriticalValue": 80.0},
        {"equipmentId": "MB003", "parameterId": "agSpeed",     "parameterName": "Agitator Speed",        "unitOfMeasure": "RPM", "idealValue": 140.0,"lowWarningValue": 120.0,"lowCriticalValue": 100.0,"highWarningValue": 160.0, "highCriticalValue": 175.0},
        {"equipmentId": "MB003", "parameterId": "chpSpeed",    "parameterName": "Granulator Speed",      "unitOfMeasure": "RPM", "idealValue": 1420.0,"lowWarningValue": 1250.0,"lowCriticalValue": 1000.0,"highWarningValue": 1500.0,"highCriticalValue": 1600.0},
        {"equipmentId": "MB003", "parameterId": "durationSec", "parameterName": "Duration Sec",          "unitOfMeasure": "Sec", "idealValue": 180.0,"lowWarningValue": 0.0,  "lowCriticalValue": 0.0,  "highWarningValue": 480.0, "highCriticalValue": 600.0},
        # FBD (MB004)
        {"equipmentId": "MB004", "parameterId": "inletTemp",   "parameterName": "Inlet Air Temperature", "unitOfMeasure": "°C",  "idealValue": 60.0, "lowWarningValue": 26.0, "lowCriticalValue": 24.0, "highWarningValue": 61.0,  "highCriticalValue": 65.0},
        {"equipmentId": "MB004", "parameterId": "exhaustTemp", "parameterName": "Exhaust Air Temperature","unitOfMeasure": "°C",  "idealValue": 37.0, "lowWarningValue": 20.0, "lowCriticalValue": 19.0, "highWarningValue": 49.0,  "highCriticalValue": 52.0},
        {"equipmentId": "MB004", "parameterId": "shakingState","parameterName": "Shaking State",         "unitOfMeasure": "state","idealValue": 0.0,  "lowWarningValue": 0.0,  "lowCriticalValue": 0.0,  "highWarningValue": 1.0,   "highCriticalValue": 1.0},
        # Blender (MB005)
        {"equipmentId": "MB005", "parameterId": "blenderSpeed","parameterName": "Blender Speed",         "unitOfMeasure": "RPM", "idealValue": 6.0,  "lowWarningValue": 5.5,  "lowCriticalValue": 5.0,  "highWarningValue": 6.5,   "highCriticalValue": 7.0},
        {"equipmentId": "MB005", "parameterId": "actualRpm",   "parameterName": "Actual Blender RPM",   "unitOfMeasure": "RPM", "idealValue": 6.0,  "lowWarningValue": 5.8,  "lowCriticalValue": 5.0,  "highWarningValue": 6.2,   "highCriticalValue": 7.0},
        {"equipmentId": "MB005", "parameterId": "mixingCountdown","parameterName": "Mixing Countdown",   "unitOfMeasure": "min", "idealValue": 10.0, "lowWarningValue": 0.0,  "lowCriticalValue": 0.0,  "highWarningValue": 10.0,  "highCriticalValue": 10.0},
        {"equipmentId": "MB005", "parameterId": "vacuumStatus","parameterName": "Vacuum Status",        "unitOfMeasure": "state","idealValue": 1.0,  "lowWarningValue": 0.0,  "lowCriticalValue": 0.0,  "highWarningValue": 1.0,   "highCriticalValue": 1.0},
        # Compression Machine (MB040)
        {"equipmentId": "MB040", "parameterId": "turretRpm",       "parameterName": "Turret RPM",             "unitOfMeasure": "RPM", "idealValue": 32.5, "lowWarningValue": 25.0, "lowCriticalValue": 20.0, "highWarningValue": 40.0,  "highCriticalValue": 45.0},
        {"equipmentId": "MB040", "parameterId": "mainCompressionForce","parameterName": "Main Compression Force","unitOfMeasure": "kN",  "idealValue": 15.0, "lowWarningValue": 12.0, "lowCriticalValue": 10.0, "highWarningValue": 18.0,  "highCriticalValue": 20.0},
        {"equipmentId": "MB040", "parameterId": "preForce",        "parameterName": "Pre-Compression Force", "unitOfMeasure": "kN",  "idealValue": 4.5,  "lowWarningValue": 3.0,  "lowCriticalValue": 2.0,  "highWarningValue": 6.0,   "highCriticalValue": 8.0},
        {"equipmentId": "MB040", "parameterId": "tabletCount",     "parameterName": "Tablet Count",           "unitOfMeasure": "tabs","idealValue": 0.0,  "lowWarningValue": 0.0,  "lowCriticalValue": 0.0,  "highWarningValue": 9999999.0,"highCriticalValue": 9999999.0},
        # Coating Machine (MB041)
        {"equipmentId": "MB041", "parameterId": "inletAirTemp",    "parameterName": "Inlet Air Temperature",  "unitOfMeasure": "°C",  "idealValue": 60.0, "lowWarningValue": 29.0, "lowCriticalValue": 25.0, "highWarningValue": 63.4,  "highCriticalValue": 68.0},
        {"equipmentId": "MB041", "parameterId": "exhaustAirTemp",  "parameterName": "Exhaust Air Temperature","unitOfMeasure": "°C",  "idealValue": 48.0, "lowWarningValue": 23.0, "lowCriticalValue": 20.0, "highWarningValue": 50.0,  "highCriticalValue": 53.0},
        {"equipmentId": "MB041", "parameterId": "bedTemp",         "parameterName": "Tablet Bed Temperature", "unitOfMeasure": "°C",  "idealValue": 48.0, "lowWarningValue": 23.6, "lowCriticalValue": 20.0, "highWarningValue": 50.5,  "highCriticalValue": 53.0},
        {"equipmentId": "MB041", "parameterId": "panSpeed",        "parameterName": "Pan Rotation Speed",     "unitOfMeasure": "RPM", "idealValue": 2.1,  "lowWarningValue": 2.0,  "lowCriticalValue": 1.8,  "highWarningValue": 2.2,   "highCriticalValue": 2.5},
        {"equipmentId": "MB041", "parameterId": "dosingSpeed",     "parameterName": "Dosing Pump Speed",      "unitOfMeasure": "RPM", "idealValue": 14.6, "lowWarningValue": 13.6, "lowCriticalValue": 10.0, "highWarningValue": 15.6,  "highCriticalValue": 19.0},
        {"equipmentId": "MB041", "parameterId": "cycleCounter",    "parameterName": "Coating Cycle Counter",  "unitOfMeasure": "cycles","idealValue": 133.0,"lowWarningValue": 0.0,  "lowCriticalValue": 0.0,  "highWarningValue": 265.0, "highCriticalValue": 300.0},
    ]

    for p in critical_params:
        _upsert(
            db["iiot_equipment_critical_parameters"],
            {"equipmentId": p["equipmentId"], "parameterId": p["parameterId"]},
            {
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "equipmentId": p["equipmentId"],
                "parameterId": p["parameterId"],
                "parameterCode": p["parameterId"],
                "parameterName": p["parameterName"],
                "unitOfMeasure": p["unitOfMeasure"],
                "parameterType": "FLOAT",
                "isCritical": True,
                "isActive": True,
            },
            now_dt,
        )
        param_limit_id = f"LIM-{p['equipmentId']}-{p['parameterId'].upper()}"
        _upsert(
            db["iiot_equipment_critical_parameters_limit"],
            {"parameterLimitId": param_limit_id},
            {
                "parameterLimitId": param_limit_id,
                "parameterLimitCode": param_limit_id,
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "equipmentId": p["equipmentId"],
                "parameterId": p["parameterId"],
                "parameterCode": p["parameterId"],
                "parameterName": p["parameterName"],
                "parameterType": "FLOAT",
                "floatValue": p["idealValue"],
                "lowWarningValue": p["lowWarningValue"],
                "lowCriticalValue": p["lowCriticalValue"],
                "highWarningValue": p["highWarningValue"],
                "highCriticalValue": p["highCriticalValue"],
                "alarmEnabled": True,
                "isActive": True,
                "effectiveFrom": datetime(2026, 1, 1),
            },
            now_dt,
        )
    print(f"✓ Upserted {len(critical_params)} critical parameters and limits (idempotent).")

    # ==========================================
    # 3. Product Master
    # ==========================================
    products = [
        {"productCode": "STGW2000", "productName": "LAMOTRIGINE",        "productCategory": "Tablets"},
        {"productCode": "STPA1D00", "productName": "PAROXETINE USP 40 mg","productCategory": "Tablets"},
    ]
    for p_col in ("products", "iiot_product_master"):
        for prod in products:
            _upsert(
                db[p_col],
                {"productCode": prod["productCode"]},
                {
                    "productId": prod["productCode"],
                    "productCode": prod["productCode"],
                    "product_code": prod["productCode"],
                    "productName": prod["productName"],
                    "product_name": prod["productName"],
                    "productCategory": prod["productCategory"],
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "isActive": True,
                },
                now_dt,
            )
    print(f"✓ Upserted {len(products)} products into catalog (idempotent).")

    # ==========================================
    # 4. Recipe Master & Recipe Management
    # ==========================================
    recipes = [
        {
            "recipeId": "RCP-AGO-01",
            "recipeCode": "AGO",
            "recipeName": "Lamotrigine Granulation & Drying Recipe (AGO)",
            "productId": "STGW2000",
            "productCode": "STGW2000",
            "productName": "LAMOTRIGINE",
            "associatedBatchSizes": ["248.640 Kg"],
            "version": "1.0",
        },
        {
            "recipeId": "RCP-BLEN-01",
            "recipeCode": "AGO0026015",
            "recipeName": "Lamotrigine Octagonal Blending Recipe (AGO0026015)",
            "productId": "STGW2000",
            "productCode": "STGW2000",
            "productName": "LAMOTRIGINE",
            "associatedBatchSizes": ["248.640 Kg"],
            "version": "1.0",
        },
        {
            "recipeId": "RCP-COMP-01",
            "recipeCode": "COMP",
            "recipeName": "Lamotrigine Compression Recipe (COMP)",
            "productId": "STGW2000",
            "productCode": "STGW2000",
            "productName": "LAMOTRIGINE",
            "associatedBatchSizes": ["248.640 Kg"],
            "version": "1.0",
        },
        {
            "recipeId": "RCP-COAT-01",
            "recipeCode": "PAROXE40",
            "recipeName": "Paroxetine USP 40mg Film Coating Recipe (PAROXE40)",
            "productId": "STPA1D00",
            "productCode": "STPA1D00",
            "productName": "PAROXETINE USP 40 mg",
            "associatedBatchSizes": ["625000 Tablets"],
            "version": "1.0",
        },
    ]

    for r in recipes:
        _upsert(
            db["iiot_recipe_master"],
            {"recipeId": r["recipeId"]},
            {**r, "tenantId": TENANT_ID, "plantId": PLANT_ID, "isActive": True},
            now_dt,
        )

    eq_spec_map = {eq["equipmentId"]: eq for eq in equipments}
    for p in critical_params:
        rcm_id = f"RCM-{p['equipmentId']}-{p['parameterId'].upper()}"
        spec = eq_spec_map[p["equipmentId"]]
        if p["equipmentId"] in ("MB003", "MB004"):
            r_code, r_name, r_size = "AGO", "Lamotrigine Granulation & Drying Recipe (AGO)", "248.640 Kg"
            prod_c, prod_n = "STGW2000", "LAMOTRIGINE"
        elif p["equipmentId"] == "MB005":
            r_code, r_name, r_size = "AGO0026015", "Lamotrigine Octagonal Blending Recipe (AGO0026015)", "248.640 Kg"
            prod_c, prod_n = "STGW2000", "LAMOTRIGINE"
        elif p["equipmentId"] == "MB040":
            r_code, r_name, r_size = "COMP", "Lamotrigine Compression Recipe (COMP)", "248.640 Kg"
            prod_c, prod_n = "STGW2000", "LAMOTRIGINE"
        else:
            r_code, r_name, r_size = "PAROXE40", "Paroxetine USP 40mg Film Coating Recipe (PAROXE40)", "625000 Tablets"
            prod_c, prod_n = "STPA1D00", "PAROXETINE USP 40 mg"

        _upsert(
            db["iiot_recipe_management"],
            {"recipeManagementId": rcm_id},
            {
                "recipeManagementId": rcm_id,
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "productId": prod_c,
                "productCode": prod_c,
                "productName": prod_n,
                "recipeId": r_code,
                "recipeCode": r_code,
                "recipeName": r_name,
                "batchSize": r_size,
                "equipmentId": p["equipmentId"],
                "equipmentCode": p["equipmentId"],
                "equipmentName": spec["equipmentName"],
                "parameterCode": p["parameterId"],
                "parameterName": p["parameterName"],
                "unitOfMeasure": p["unitOfMeasure"],
                "uom": p["unitOfMeasure"],
                "targetSetpoint": p["idealValue"],
                "lowLimit": p["lowCriticalValue"],
                "highLimit": p["highCriticalValue"],
                "isActive": True,
            },
            now_dt,
        )
    print(f"✓ Upserted {len(recipes)} recipes and {len(critical_params)} recipe management links (idempotent).")

    # ==========================================
    # 5. User Master & Profile Synchronization
    # ==========================================
    users = [
        # MB003 Operators/Supervisors
        {"userId": "96828",      "username": "96828",      "email": "operator_96828@adavis.com",      "firstName": "PB1 RMG",              "lastName": "Operator",    "role": "OPERATOR",  "displayLabel": "Operator",    "groupId": "GRP-0011", "context": "MB003 (RMG) / MODULE-B"},
        {"userId": "96365",      "username": "96365",      "email": "supervisor_96365@adavis.com",    "firstName": "PB1 RMG",              "lastName": "Supervisor",  "role": "REVIEWER",  "displayLabel": "Supervisor",  "groupId": "GRP-0012", "context": "MB003 (RMG) / MODULE-B"},
        # MB004 / MB005 Operators/Supervisors
        {"userId": "11173",      "username": "11173",      "email": "operator_11173@adavis.com",      "firstName": "PB1 Module-B",         "lastName": "Operator",    "role": "OPERATOR",  "displayLabel": "Operator",    "groupId": "GRP-0011", "context": "MB004 (FBD) & MB005 (Blender)"},
        {"userId": "191555",     "username": "191555",     "email": "supervisor_191555@adavis.com",   "firstName": "PB1 Module-B (MB004)", "lastName": "Supervisor",  "role": "REVIEWER",  "displayLabel": "Supervisor",  "groupId": "GRP-0012", "context": "MB004 (FBD) / MODULE-B"},
        {"userId": "191164",     "username": "191164",     "email": "supervisor_191164@adavis.com",   "firstName": "Harish Chandra",       "lastName": "Mishra",      "role": "REVIEWER",  "displayLabel": "Supervisor",  "groupId": "GRP-0012", "context": "MB005 (Blender) / MODULE-B"},
        # MB041 Operators/Supervisors
        {"userId": "29995",      "username": "29995",      "email": "operator_29995@adavis.com",      "firstName": "PB1 Coating",          "lastName": "Operator",    "role": "OPERATOR",  "displayLabel": "Operator",    "groupId": "GRP-0011", "context": "MB041 (Coating Machine) / COATING MODULE-B"},
        {"userId": "191257",     "username": "191257",     "email": "supervisor_191257@adavis.com",   "firstName": "PB1 Coating",          "lastName": "Supervisor",  "role": "REVIEWER",  "displayLabel": "Supervisor",  "groupId": "GRP-0012", "context": "MB041 (Coating Machine) / COATING MODULE-B"},
        # MB040 Operators/Supervisors
        {"userId": "10401",      "username": "10401",      "email": "operator_10401@adavis.com",      "firstName": "PB1 Compression",      "lastName": "Operator",    "role": "OPERATOR",  "displayLabel": "Operator",    "groupId": "GRP-0011", "context": "MB040 (Compression) / MODULE-B"},
        {"userId": "10402",      "username": "10402",      "email": "supervisor_10402@adavis.com",    "firstName": "PB1 Compression",      "lastName": "Supervisor",  "role": "REVIEWER",  "displayLabel": "Supervisor",  "groupId": "GRP-0012", "context": "MB040 (Compression) / MODULE-B"},
        # Approvers
        {"userId": "USR-QA-01",  "username": "qa_approver",    "email": "qa_approver@adavis.com",    "firstName": "Quality",              "lastName": "Approver",    "role": "APPROVER",  "displayLabel": "Approver",    "groupId": "GRP-0017", "context": "Plant QA Release / All Stages"},
        {"userId": "APPROVER-01","username": "approver_pb1",   "email": "approver_pb1@adavis.com",   "firstName": "PB1 Production",       "lastName": "Approver",    "role": "APPROVER",  "displayLabel": "Approver",    "groupId": "GRP-0017", "context": "PB1 Production Plant / All Stages"},
    ]

    for idx, u in enumerate(users):
        _upsert(
            db["auth_users"],
            {"userId": u["userId"]},
            {
                "userId": u["userId"],
                "username": u["username"],
                "email": u["email"],
                "firstName": u["firstName"],
                "lastName": u["lastName"],
                "role": u["role"],
                "status": "ACTIVE",
                "isLocked": False,
                "failedAttempts": 0,
                "isActive": True,
            },
            now_dt,
        )
        _upsert(
            db["auth_credentials"],
            {"userId": u["userId"]},
            {
                "userId": u["userId"],
                "passwordHash": DEFAULT_PASSWORD_HASH,
            },
            now_dt,
        )
        _upsert(
            db["mdm_user_auth_credentials"],
            {"userId": u["userId"]},
            {
                "userId": u["userId"],
                "email": u["email"],
                "passwordHash": DEFAULT_PASSWORD_HASH,
                "mustChangePassword": False,
                "passwordUpdatedAt": now_dt,
            },
            now_dt,
        )
        _upsert(
            db["mdm_user_profiles"],
            {"userId": u["userId"]},
            {
                "userId": u["userId"],
                "userTrackId": f"USR-{2000+idx}",
                "firstName": u["firstName"],
                "lastName": u["lastName"],
                "email": u["email"],
                "phone": "+91 9876543210",
                "isActive": True,
                "isBlocked": False,
                "isExternal": False,
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
            },
            now_dt,
        )
        _upsert(
            db["mdm_user_assignments_to_user_groups"],
            {"userId": u["userId"], "groupId": u["groupId"]},
            {
                "userId": u["userId"],
                "groupId": u["groupId"],
                "isActive": True,
                "assignedAt": now_dt,
                "assignedBy": "SUPER_ADMIN",
            },
            now_dt,
        )
        _upsert(
            db["mdm_user_context_assignments"],
            {"assignmentId": f"ASGN-{u['userId']}"},
            {
                "assignmentId": f"ASGN-{u['userId']}",
                "tenantId": TENANT_ID,
                "userId": u["userId"],
                "groupId": u["groupId"],
                "plantId": PLANT_ID,
                "departmentId": "DPT-0006",
                "isActive": True,
            },
            now_dt,
        )

    # Ensure default operators also have matching default password
    for op_id in ("PRODUCTION_OPERATOR_1", "PRODUCTION_OPERATOR_2", "PRODUCTION_OPERATOR_3"):
        db["mdm_user_auth_credentials"].update_one(
            {"userId": op_id},
            {"$set": {"passwordHash": DEFAULT_PASSWORD_HASH, "mustChangePassword": False, "updatedAt": now_dt}}
        )

    # Ensure accounts are unlocked and active
    all_user_ids = [u["userId"] for u in users] + [
        "PRODUCTION_OPERATOR_1", "PRODUCTION_OPERATOR_2", "PRODUCTION_OPERATOR_3",
        "PRODUCTION_REVIEWER_1", "QA_APPROVER_1", "SUPER_ADMIN", "IT_ADMIN"
    ]
    db["auth_users"].update_many(
        {"userId": {"$in": all_user_ids}},
        {"$set": {"isLocked": False, "failedAttempts": 0, "status": "ACTIVE"}}
    )

    print(f"✓ Upserted {len(users)} users into auth, MDM credentials, group and context assignments (idempotent).")

    # ==========================================
    # 6. Batch Summary (5 batches, 1 per equipment/lot)
    # ==========================================
    batches = [
        {
            "filter": {"batchNo": "AGO0026016", "lotNo": "01", "lineId": "PB1", "productCode": "STGW2000"},
            "doc": {
                "batchNo": "AGO0026016",
                "lotNo": "01",
                "lineId": "PB1",
                "productCode": "STGW2000",
                "productName": "LAMOTRIGINE",
                "recipeName": "Lamotrigine Granulation & Drying Recipe (AGO)",
                "overallStatus": "IN_PROGRESS",
                "stages": [
                    {
                        "stageId": "STAGE-1",
                        "stageName": "Granulation",
                        "equipmentType": "RMG",
                        "equipmentCode": "MB003",
                        "equipmentId": "MB003",
                        "sequenceOrder": 1,
                        "sequence": 1,
                        "executionStatus": "IN_PROGRESS",
                        "stageStartAt": now_dt,
                        "stageEndAt": now_dt,
                        "operatorName": "96828 (PB1-RMG (MB003) Operator)",
                        "supervisorName": "96365 (PB1-RMG (MB003) Supervisor)",
                        "recordCount": 1,
                        "approval": {"status": "PENDING", "approvedBy": "", "approvedAt": None, "comments": ""},
                    }
                ],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
            },
        },
        {
            "filter": {"batchNo": "AGO0026016", "lotNo": "1B", "lineId": "PB1", "productCode": "STGW2000"},
            "doc": {
                "batchNo": "AGO0026016",
                "lotNo": "1B",
                "lineId": "PB1",
                "productCode": "STGW2000",
                "productName": "LAMOTRIGINE",
                "recipeName": "Lamotrigine Granulation & Drying Recipe (AGO)",
                "overallStatus": "IN_PROGRESS",
                "stages": [
                    {
                        "stageId": "STAGE-2",
                        "stageName": "Drying",
                        "equipmentType": "FBD",
                        "equipmentCode": "MB004",
                        "equipmentId": "MB004",
                        "sequenceOrder": 2,
                        "sequence": 2,
                        "executionStatus": "IN_PROGRESS",
                        "stageStartAt": now_dt,
                        "stageEndAt": now_dt,
                        "operatorName": "11173 (PB1-Module-B (MB004) Operator)",
                        "supervisorName": "191555 (PB1-Module-B (MB004) Supervisor)",
                        "recordCount": 1,
                        "approval": {"status": "PENDING", "approvedBy": "", "approvedAt": None, "comments": ""},
                    }
                ],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
            },
        },
        {
            "filter": {"batchNo": "AGO0026015", "lotNo": "01", "lineId": "PB1", "productCode": "STGW2000"},
            "doc": {
                "batchNo": "AGO0026015",
                "lotNo": "01",
                "lineId": "PB1",
                "productCode": "STGW2000",
                "productName": "LAMOTRIGINE",
                "recipeName": "Lamotrigine Octagonal Blending Recipe (AGO0026015)",
                "overallStatus": "IN_PROGRESS",
                "stages": [
                    {
                        "stageId": "STAGE-3",
                        "stageName": "Blending",
                        "equipmentType": "BLE",
                        "equipmentCode": "MB005",
                        "equipmentId": "MB005",
                        "sequenceOrder": 3,
                        "sequence": 3,
                        "executionStatus": "IN_PROGRESS",
                        "stageStartAt": now_dt,
                        "stageEndAt": now_dt,
                        "operatorName": "11173 (PB1-Module-B-Blender-Operator)",
                        "supervisorName": "Harish Chandra Mishra-191164(PB1-Module-B-Blender-Supervisor)",
                        "recordCount": 1,
                        "approval": {"status": "PENDING", "approvedBy": "", "approvedAt": None, "comments": ""},
                    }
                ],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
            },
        },
        {
            "filter": {"batchNo": "Pb1 Mb Compression", "lotNo": "01", "lineId": "PB1"},
            "doc": {
                "batchNo": "Pb1 Mb Compression",
                "lotNo": "01",
                "lineId": "PB1",
                "productCode": "STGW2000",
                "productName": "LAMOTRIGINE",
                "recipeName": "Lamotrigine Compression Recipe (COMP)",
                "overallStatus": "IN_PROGRESS",
                "stages": [
                    {
                        "stageId": "STAGE-4",
                        "stageName": "Compression",
                        "equipmentType": "COMP",
                        "equipmentCode": "MB040",
                        "equipmentId": "MB040",
                        "sequenceOrder": 4,
                        "sequence": 4,
                        "executionStatus": "IN_PROGRESS",
                        "stageStartAt": now_dt,
                        "stageEndAt": now_dt,
                        "operatorName": "10401 (PB1-Compression-Operator)",
                        "supervisorName": "10402 (PB1-Compression-Supervisor)",
                        "recordCount": 1,
                        "approval": {"status": "PENDING", "approvedBy": "", "approvedAt": None, "comments": ""},
                    }
                ],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
            },
        },
        {
            "filter": {"batchNo": "PED26009", "lotNo": "NA", "lineId": "PB1"},
            "doc": {
                "batchNo": "PED26009",
                "lotNo": "NA",
                "lineId": "PB1",
                "productCode": "STPA1D00",
                "productName": "PAROXETINE USP 40 mg",
                "recipeName": "Paroxetine USP 40mg Film Coating Recipe (PAROXE40)",
                "overallStatus": "IN_PROGRESS",
                "stages": [
                    {
                        "stageId": "STAGE-5",
                        "stageName": "Coating",
                        "equipmentType": "COAT",
                        "equipmentCode": "MB041",
                        "equipmentId": "MB041",
                        "sequenceOrder": 5,
                        "sequence": 5,
                        "executionStatus": "IN_PROGRESS",
                        "stageStartAt": now_dt,
                        "stageEndAt": now_dt,
                        "operatorName": "29995 (PB1-Module-B-Operator)",
                        "supervisorName": "191257 (PB1-Module-B-Supervisor)",
                        "recordCount": 1,
                        "approval": {"status": "PENDING", "approvedBy": "", "approvedAt": None, "comments": ""},
                    }
                ],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
            },
        },
    ]

    # Insert if missing, or update recipeName if already present
    for batch_entry in batches:
        existing = db["iiot_batch_summary"].find_one(batch_entry["filter"])
        if existing is None:
            doc = {**batch_entry["doc"], "createdAt": now_dt, "updatedAt": now_dt}
            db["iiot_batch_summary"].insert_one(doc)
        else:
            db["iiot_batch_summary"].update_one(
                batch_entry["filter"],
                {"$set": {"recipeName": batch_entry["doc"]["recipeName"]}}
            )
    print(f"✓ Ensured {len(batches)} batch summary records exist with synchronized recipe names.")

    # ==========================================
    # 7. Live Status & Redis Pre-warming (all 5)
    # ==========================================
    live_records = [
        {
            "code": "MB003",
            "assetId": "10094",
            "batch": "AGO0026016",
            "lot": "01",
            "product": "LAMOTRIGINE",
            "prodCode": "STGW2000",
            "op": "96828 (PB1-RMG (MB003) Operator)",
            "sup": "96365 (PB1-RMG (MB003) Supervisor)",
            "tags": {
                "CURRENT (Amp)": 22.4,
                "Impeller_Current_Amp": 22.4,
                "Chopper_Current_Amp": 4.5,
                "Pump_RPM": 60.0,
                "agAmps": 22.4,
                "chpAmps": 4.5,
                "pumpRpm": 60.0,
                "STATUS": "WET MIXING",
            },
        },
        {
            "code": "MB004",
            "assetId": "10110",
            "batch": "AGO0026016",
            "lot": "1B",
            "product": "LAMOTRIGINE",
            "prodCode": "STGW2000",
            "op": "11173 (PB1-Module-B (MB004) Operator)",
            "sup": "191555 (PB1-Module-B (MB004) Supervisor)",
            "tags": {
                "INLET TEMPARATURE": 57.0,
                "EXHAUST TEMPARATURE": 29.0,
                "Inlet_Temp": 57.0,
                "Exhaust_Temp": 29.0,
                "Shaking_State": "DRYING",
                "inletTemp": 57.0,
                "exhaustTemp": 29.0,
            },
        },
        {
            "code": "MB005",
            "assetId": "10095",
            "batch": "AGO0026015",
            "lot": "01",
            "product": "LAMOTRIGINE",
            "prodCode": "STGW2000",
            "op": "11173 (PB1-Module-B-Blender-Operator)",
            "sup": "Harish Chandra Mishra-191164(PB1-Module-B-Blender-Supervisor)",
            "tags": {
                "BLENDING SPEED (RPM)": 6.0,
                "Blender_Speed_RPM": 6.0,
                "ACTUAL RPM": 6,
                "actualRpm": 6,
                "BLENDER STATUS": "MIXING 1 STARTED",
                "blenderStatus": "MIXING 1 STARTED",
                "Mixing_Countdown_Min": 10,
                "mixingCountdown": 10,
                "Vacuum_Status": "ON",
                "vacuumStatus": "ON",
                "Mixing_No": 1,
                "mixingNo": 1,
                "blenderSpeed": 6.0,
            },
        },
        {
            "code": "MB040",
            "assetId": "10040",
            "batch": "Pb1 Mb Compression",
            "lot": "01",
            "product": "LAMOTRIGINE",
            "prodCode": "STGW2000",
            "op": "10401 (PB1-Compression-Operator)",
            "sup": "10402 (PB1-Compression-Supervisor)",
            "tags": {
                "Turret_RPM": 32.5,
                "turretRpm": 32.5,
                "Main_Force_kN": 15.2,
                "mainCompressionForce": 15.2,
                "Pre_Force_kN": 4.5,
                "preForce": 4.5,
                "Tablet_Count": 150000,
                "tabletCount": 150000,
                "STATUS": "RUNNING",
            },
        },
        {
            "code": "MB041",
            "assetId": "10141",
            "batch": "PED26009",
            "lot": "NA",
            "product": "PAROXETINE USP 40 mg",
            "prodCode": "STPA1D00",
            "op": "29995 (PB1-Module-B-Operator)",
            "sup": "191257 (PB1-Module-B-Supervisor)",
            "tags": {
                "INLET AIR TEMP (°C)": 60.0,
                "EXHAUST AIR TEMP (°C)": 48.0,
                "BED TEMP (°C)": 48.0,
                "DOSING PUMP SPEED (RPM)": 14.0,
                "PAN SPEED (RPM)": 2.1,
                "CYCLE COUNTER": 133,
                "Inlet_Air_Temp": 60.0,
                "Exhaust_Air_Temp": 48.0,
                "Bed_Temp": 48.0,
                "Dosing_Speed_RPM": 14.0,
                "Pan_Speed_RPM": 2.1,
                "Cycle_Counter": 133,
                "inletAirTemp": 60.0,
                "exhaustAirTemp": 48.0,
                "bedTemp": 48.0,
                "dosingSpeed": 14.0,
                "panSpeed": 2.1,
                "cycleCounter": 133,
                "Coat_Status": "SPRAYING",
                "coatStatus": "SPRAYING",
            },
        },
    ]

    for item in live_records:
        live_doc = {
            "equipmentId": item["code"],
            "equipmentCode": item["code"],
            "assetId": item["assetId"],
            "currentState": "Running",
            "state": "RUNNING",
            "stateReason": f"Batch in progress: {item['batch']}",
            "lastBatchNo": item["batch"],
            "lastLotNo": item["lot"],
            "batchNo": item["batch"],
            "lotNo": item["lot"],
            "activeBatch": item["batch"],
            "activeLot": item["lot"],
            "productCode": item["prodCode"],
            "productName": item["product"],
            "operator": item["op"],
            "operatorName": item["op"],
            "supervisor": item["sup"],
            "supervisorName": item["sup"],
            "telemetry": item["tags"],
            "tags": item["tags"],
            "lastEventAt": now_dt.isoformat(),
            "heartbeatAt": now_dt.isoformat(),
        }
        _upsert(db["iiot_equipment_live_status"], {"equipmentId": item["code"]}, live_doc, now_dt)

        if r_client is not None:
            cache_payload = {
                "assetCode": item["code"],
                "equipmentCode": item["code"],
                "assetId": item["assetId"],
                "batchNo": item["batch"],
                "lotNo": item["lot"],
                "productCode": item["prodCode"],
                "productName": item["product"],
                "operator": item["op"],
                "operatorName": item["op"],
                "supervisor": item["sup"],
                "supervisorName": item["sup"],
                "state": "RUNNING",
                "alarm": "NONE",
                "tags": item["tags"],
                "updatedAt": now_dt.isoformat(),
            }
            r_client.set(f"iiot:realtime:{item['code']}", json.dumps(cache_payload, default=str), ex=86400)
            r_client.set(f"iiot:realtime:{item['assetId']}", json.dumps(cache_payload, default=str), ex=86400)

    print(f"✓ Upserted live status and Redis real-time cache for {len(live_records)} target equipments (idempotent).")

    # ==========================================
    # 8. Canonical Timeseries Alarms & Audits (matching sample PDFs)
    # ==========================================
    def parse_dt(dt_str):
        return datetime.strptime(dt_str, "%d/%m/%Y %H:%M:%S")

    rmg_audits = [
        ("30/09/2026 02:46:36", "BATCH START", "96365 (PB1-RMG (MB003) Supervisor)"),
        ("30/09/2026 02:53:57", "SELECT MODE AUTO", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:54:19", "AUTO START", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:55:00", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:55:36", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:55:40", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:55:49", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:56:06", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:56:29", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:56:39", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:57:07", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:57:42", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:58:36", "AUTO PAUSE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 02:58:36", "AUTO PAUSE REASON: BINDER/GRANULATING AGENT ADDITION", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 03:00:15", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 03:00:23", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 03:00:30", "AUTO CONTINUE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 03:17:59", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 05:15:39", "AUTO UNLOADING START: BOWL CHANGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 05:15:47", "ACKNOWLEDGE", "96828 (PB1-RMG (MB003) Operator)"),
        ("30/09/2026 05:17:36", "BATCH END: PROCESS OVER", "191555 (PB1-RMG (MB003) Supervisor)"),
    ]

    fbd_audits = [
        ("30/09/2026 03:22:20", "BATCH START", "191555 (PB1-FBD (MB004) Supervisor)"),
        ("30/09/2026 03:23:44", "SELECT MODE AUTO", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 03:24:26", "PC SEAL ON", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 03:25:01", "AUTO START", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 03:40:20", "AUTO STOP: RAKING", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 03:41:00", "PC SEAL OFF", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 03:43:40", "PC SEAL ON", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 03:44:10", "AUTO START", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 04:05:00", "AUTO STOP: LOD CHECK", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 04:05:30", "PC SEAL OFF", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 04:15:20", "PC SEAL ON", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 04:15:45", "AUTO START", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 04:45:00", "AUTO STOP: PROCESS OVER", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 04:46:15", "PC SEAL OFF", "11173 (PB1-FBD (MB004) Operator)"),
        ("30/09/2026 04:50:38", "BATCH END: PROCESS OVER", "191164 (PB1-FBD (MB004) Supervisor)"),
    ]

    ble_audits = [
        ("29/09/2026 23:27:38", "BATCH START", "96365 (PB1-Module-B-Blender-Supervisor)"),
        ("29/09/2026 23:28:10", "SELECT MODE AUTO", "96365 (PB1-Module-B-Blender-Supervisor)"),
        ("29/09/2026 23:30:00", "BLEND START", "96365 (PB1-Module-B-Blender-Supervisor)"),
        ("30/09/2026 00:10:00", "HOME POS INCH", "11173 (PB1-Module-B-Blender-Operator)"),
        ("30/09/2026 00:20:00", "BLEND START", "11173 (PB1-Module-B-Blender-Operator)"),
        ("30/09/2026 00:35:00", "HOME POSITION", "11173 (PB1-Module-B-Blender-Operator)"),
        ("30/09/2026 00:40:12", "BATCH END", "11173 (PB1-Module-B-Blender-Operator)"),
    ]

    ble_alarms = [
        ("29/09/2026 23:41:22", "30/09/2026 00:05:31", "00:24:09", 101, "SAFTY GUARD OPEN"),
        ("30/09/2026 00:11:41", "30/09/2026 00:17:28", "00:05:47", 102, "SAFTY GUARD OPEN"),
        ("30/09/2026 00:33:30", "30/09/2026 00:34:04", "00:00:34", 103, "SAFTY GUARD OPEN"),
    ]

    comp_audits = [
        ("30/09/2026 05:45:00", "BATCH START", "10402 (PB1-Compression-Operator)"),
        ("30/09/2026 05:47:15", "COMPRESSION START", "10402 (PB1-Compression-Operator)"),
        ("30/09/2026 06:14:30", "MAIN FORCE PARAMETER ADJUST", "10402 (PB1-Compression-Operator)"),
        ("30/09/2026 07:10:00", "COMPRESSION STOP", "10401 (PB1-Compression-Supervisor)"),
        ("30/09/2026 07:15:22", "BATCH END", "10401 (PB1-Compression-Supervisor)"),
    ]

    comp_alarms = [
        ("30/09/2026 06:14:22", "30/09/2026 06:14:40", "00:00:18", 201, "MAIN FORCE LIMIT HIGH"),
    ]

    coat_audits = [
        ("20/09/2026 15:43:08", "BATCH START", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 15:44:03", "TABLET LOADING START", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 15:49:33", "TABLET LOADING END", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 15:53:23", "EXHAUST DAMPER OPENING 100%", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 15:53:23", "DE DUSTING START", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 15:58:24", "DE DUSTING OVER", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 16:04:19", "AGITATOR SOLUTION TANK-1 ON", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 16:07:05", "DOSING START", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 16:11:47", "DOSING STOP", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 16:16:32", "HEATING START", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 16:21:40", "COATING START", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 16:26:01", "AUTO MODE START", "191257 (PB1-Coating-Supervisor)"),
        ("20/09/2026 16:38:40", "PRE JOG", "29995 (PB1-Coating-Operator)"),
        ("20/09/2026 18:41:00", "AGITATOR SOLUTION TANK-1 OFF", "8585 (PB1-Coating-Operator)"),
        ("20/09/2026 20:53:08", "AUTO MODE INTERRUPT: FILTER CHOKING", "8585 (PB1-Coating-Operator)"),
        ("20/09/2026 21:05:40", "AUTO MODE RESUME", "8585 (PB1-Coating-Operator)"),
        ("20/09/2026 22:30:15", "AUTO STOP", "8585 (PB1-Coating-Operator)"),
        ("20/09/2026 22:35:00", "DRYING START", "8585 (PB1-Coating-Operator)"),
        ("20/09/2026 22:45:00", "COOLING START", "8585 (PB1-Coating-Operator)"),
        ("20/09/2026 22:49:59", "PROCESS OVER", "8585 (PB1-Coating-Operator)"),
        ("20/09/2026 22:50:35", "BATCH END", "191164 (PB1-Coating-Supervisor)"),
    ]

    coat_alarms = [
        ("20/09/2026 22:49:59", "20/09/2026 22:50:08", "00:00:09", 301, "PROCESS OVER"),
    ]

    def _seed_ts_audits(eq_code, eq_type, audit_list):
        col = db[f"iiot_ts_audit_{eq_code}"]
        col.delete_many({})
        docs = []
        for idx, (dt_s, desc, user) in enumerate(audit_list, 1):
            dt = parse_dt(dt_s)
            docs.append({
                "record_id": f"AUD-{eq_code}-{idx:02d}",
                "description": desc,
                "action": desc,
                "event_time": dt,
                "eventAt": dt_s,
                "ingested_at": now_dt,
                "meta": {"equipment_code": eq_code, "equipment_type": eq_type},
                "new_value": "-",
                "old_value": "-",
                "reason": "-",
                "source": {"dataset_id": eq_code},
                "user_name": user,
                "userId": user,
                "tenantId": TENANT_ID
            })
        if docs:
            col.insert_many(docs)

    def _seed_ts_alarms(eq_code, eq_type, alarm_list):
        col = db[f"iiot_ts_alarm_{eq_code}"]
        col.delete_many({})
        docs = []
        for idx, (occ_s, res_s, dur, msg_no, msg_txt) in enumerate(alarm_list, 1):
            dt = parse_dt(occ_s)
            docs.append({
                "record_id": f"ALM-{eq_code}-{idx:02d}",
                "alarm_name": msg_txt,
                "msg_text": msg_txt,
                "msg_number": msg_no,
                "occurred_time": occ_s,
                "resolved_time": res_s,
                "duration": dur,
                "event_time": dt,
                "dt": occ_s,
                "ingested_at": now_dt,
                "meta": {"equipment_code": eq_code, "equipment_type": eq_type},
                "source": {"dataset_id": eq_code},
                "status": "RESOLVED",
                "state_after": 1,
                "severity": "CRITICAL" if "LIMIT" in msg_txt or "OPEN" in msg_txt else "WARNING",
                "tenantId": TENANT_ID
            })
        if docs:
            col.insert_many(docs)

    _seed_ts_audits("MB003", "RMG", rmg_audits)
    _seed_ts_alarms("MB003", "RMG", [])

    _seed_ts_audits("MB004", "FBD", fbd_audits)
    _seed_ts_alarms("MB004", "FBD", [])

    _seed_ts_audits("MB005", "BLE", ble_audits)
    _seed_ts_alarms("MB005", "BLE", ble_alarms)

    _seed_ts_audits("MB040", "COMP", comp_audits)
    _seed_ts_alarms("MB040", "COMP", comp_alarms)

    _seed_ts_audits("MB041", "COAT", coat_audits)
    _seed_ts_alarms("MB041", "COAT", coat_alarms)

    print(f"✓ Seeded canonical timeseries alarms & audits matching sample PDFs for all 5 equipment.")
    print("\n✅ Master Data Synchronization & Seed complete (all 5 equipment, idempotent).")


if __name__ == "__main__":
    sync_all()
